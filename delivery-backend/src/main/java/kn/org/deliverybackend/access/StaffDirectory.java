package kn.org.deliverybackend.access;

import kn.org.deliverybackend.entity.StaffMember;
import kn.org.deliverybackend.integration.keycloak.KeycloakAdminClient;
import kn.org.deliverybackend.repository.StaffMemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a Keycloak login into a staff member and what they may do.
 *
 * <p>The first time someone holding the Keycloak {@code admin} or
 * {@code moderator} role opens the panel, a staff row is created for them
 * (people added on the Staff page already have one). After that the database
 * row is the authority for role and permissions, so a change on the Staff page
 * applies on the very next request. Anyone without either role gets no staff
 * row and no access.
 *
 * <p>Lookups are cached briefly because the admin panel fires several
 * requests per screen. Every change to a staff member calls {@link #evict},
 * so the cache never delays a grant or a revoke; the short lifetime only
 * covers edits made directly in the database.
 */
@Service
@Slf4j
public class StaffDirectory {

    static final String KEYCLOAK_ROLE_ADMIN = "admin";
    static final String KEYCLOAK_ROLE_MODERATOR = "moderator";

    private static final Duration CACHE_FOR = Duration.ofSeconds(15);
    private static final Duration LAST_SEEN_EVERY = Duration.ofMinutes(5);

    private final StaffMemberRepository staffRepository;
    private final TransactionTemplate tx;
    private final KeycloakAdminClient keycloak;

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    /** One lock per Keycloak user, so a burst of first requests creates exactly one row. */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public StaffDirectory(StaffMemberRepository staffRepository, PlatformTransactionManager transactionManager,
                          KeycloakAdminClient keycloak) {
        this.staffRepository = staffRepository;
        this.tx = new TransactionTemplate(transactionManager);
        this.keycloak = keycloak;
    }

    private record Cached(Optional<StaffView> view, Instant loadedAt) {
        boolean fresh() {
            return loadedAt.plus(CACHE_FOR).isAfter(Instant.now());
        }
    }

    /** The staff member behind this token, or empty if the login is not a staff account. */
    public Optional<StaffView> resolve(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) return Optional.empty();

        Cached cached = cache.get(subject);
        if (cached != null && cached.fresh()) return cached.view();

        synchronized (locks.computeIfAbsent(subject, k -> new Object())) {
            cached = cache.get(subject);
            if (cached != null && cached.fresh()) return cached.view();
            Optional<StaffView> view = Objects.requireNonNull(tx.execute(status -> load(jwt)));
            cache.put(subject, new Cached(view, Instant.now()));
            return view;
        }
    }

    /** Forget one person's cached access, so their next request reads the database. */
    public void evict(String keycloakUserId) {
        if (keycloakUserId != null) cache.remove(keycloakUserId);
    }

    public void evictAll() {
        cache.clear();
    }

    // ── internals ───────────────────────────────────────────────────────────

    private Optional<StaffView> load(Jwt jwt) {
        String subject = jwt.getSubject();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        StaffMember member = staffRepository.findByKeycloakUserId(subject).orElse(null);

        if (member == null) {
            StaffMember.Role role = roleFromToken(jwt);
            if (role == null) return Optional.empty();
            // A deleted person's token stays valid for a few minutes; it must
            // not bring them back as a new, empty staff member.
            if (!keycloak.loginExists(subject)) {
                log.info("Refused a token for Keycloak user {}: that login no longer exists", subject);
                return Optional.empty();
            }
            member = new StaffMember();
            member.setKeycloakUserId(subject);
            member.setRole(role);
            member.setCreatedBy("first login");
            member.setLastSeenAt(now);
            copyProfile(jwt, member);
            member = staffRepository.saveAndFlush(member);
            log.info("Staff member created on first login: {} as {}", member.getUsername(), role);
        } else {
            boolean changed = copyProfile(jwt, member);
            if (member.getLastSeenAt() == null || member.getLastSeenAt().isBefore(now.minus(LAST_SEEN_EVERY))) {
                member.setLastSeenAt(now);
                changed = true;
            }
            if (changed) member = staffRepository.save(member);
        }
        return Optional.of(toView(member));
    }

    static StaffView toView(StaffMember m) {
        return new StaffView(m.getId(), m.getKeycloakUserId(), m.getUsername(), m.getEmail(),
                m.displayName(), m.getRole(), m.isActive(), Set.copyOf(m.getPermissions()));
    }

    /** The staff role this token carries in Keycloak, or null for a non-staff login. */
    static StaffMember.Role roleFromToken(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> access)) return null;
        Object roles = access.get("roles");
        if (!(roles instanceof Collection<?> list)) return null;
        if (list.contains(KEYCLOAK_ROLE_ADMIN)) return StaffMember.Role.ADMIN;
        if (list.contains(KEYCLOAK_ROLE_MODERATOR)) return StaffMember.Role.MODERATOR;
        return null;
    }

    /**
     * Keep name and email in step with Keycloak, where people edit them. Role
     * and permissions are never taken from the token after the first login.
     */
    private static boolean copyProfile(Jwt jwt, StaffMember member) {
        boolean changed = false;
        String username = firstNonBlank(jwt.getClaimAsString("preferred_username"), member.getUsername(), jwt.getSubject());
        String email = firstNonBlank(jwt.getClaimAsString("email"), member.getEmail());
        String name = firstNonBlank(jwt.getClaimAsString("name"),
                joinName(jwt.getClaimAsString("given_name"), jwt.getClaimAsString("family_name")),
                member.getFullName());
        if (!Objects.equals(username, member.getUsername())) { member.setUsername(username); changed = true; }
        if (!Objects.equals(email, member.getEmail())) { member.setEmail(email); changed = true; }
        if (!Objects.equals(name, member.getFullName())) { member.setFullName(name); changed = true; }
        return changed;
    }

    private static String joinName(String first, String last) {
        String joined = ((first == null ? "" : first.trim()) + " " + (last == null ? "" : last.trim())).trim();
        return joined.isEmpty() ? null : joined;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return null;
    }
}
