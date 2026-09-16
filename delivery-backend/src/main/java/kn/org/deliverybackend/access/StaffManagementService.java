package kn.org.deliverybackend.access;

import kn.org.deliverybackend.dto.staff.PermissionTemplateDTO;
import kn.org.deliverybackend.dto.staff.PermissionTemplateRequest;
import kn.org.deliverybackend.dto.staff.StaffCreateRequest;
import kn.org.deliverybackend.dto.staff.StaffDTO;
import kn.org.deliverybackend.dto.staff.StaffUpdateRequest;
import kn.org.deliverybackend.entity.PermissionTemplate;
import kn.org.deliverybackend.entity.StaffMember;
import kn.org.deliverybackend.exception.DuplicateResourceException;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.PermissionDeniedException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.integration.keycloak.KeycloakAdminClient;
import kn.org.deliverybackend.reporting.BusinessCalendar;
import kn.org.deliverybackend.repository.PermissionTemplateRepository;
import kn.org.deliverybackend.repository.StaffMemberRepository;
import kn.org.deliverybackend.util.SearchText;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Staff page: adding people, their logins, and what they may do.
 *
 * <p>Safety rules, enforced here whatever the admin panel shows:
 * <ul>
 *   <li>Nobody deactivates, deletes or demotes themselves, changes their own
 *       permissions, or resets their own password here.</li>
 *   <li>The last active admin can't be deactivated, deleted or demoted.</li>
 *   <li>Only admins add admins, change roles, or touch an admin's account.</li>
 *   <li>A moderator who may give permissions can only give or take away
 *       permissions they hold themselves.</li>
 *   <li>Deleting needs the person's username typed as confirmation.</li>
 * </ul>
 * Every change clears the person's cached access once it is saved, so it
 * applies on their very next click.
 */
@Service
@RequiredArgsConstructor
public class StaffManagementService {

    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Map<String, Perm> PERMS =
            Arrays.stream(Perm.values()).collect(Collectors.toUnmodifiableMap(Perm::key, Function.identity()));

    private final StaffMemberRepository staffRepository;
    private final PermissionTemplateRepository templateRepository;
    private final KeycloakAdminClient keycloak;
    private final StaffDirectory directory;
    private final BusinessCalendar calendar;

    // ── staff ───────────────────────────────────────────────────────────────

    /** One page of the staff list; {@code q} searches the full name, username and email. */
    @Transactional(readOnly = true)
    public Page<StaffDTO> list(String q, int page, int size) {
        StaffView me = actor();
        return staffRepository.findForAdmin(SearchText.likePattern(q),
                        PageRequest.of(Math.max(page, 0), SearchText.pageSize(size)))
                .map(m -> toDTO(m, me));
    }

    @Transactional
    public StaffDTO create(StaffCreateRequest request) {
        StaffView me = actor();
        StaffMember.Role role = request.role() == null ? StaffMember.Role.MODERATOR : request.role();
        if (role == StaffMember.Role.ADMIN && !me.isAdmin()) {
            throw new PermissionDeniedException("Only an admin can add another admin.");
        }
        Set<String> permissions = role == StaffMember.Role.ADMIN ? Set.of() : known(request.permissions());
        if (!permissions.isEmpty()) {
            if (!me.has(Perm.STAFF_PERMISSIONS)) throw new PermissionDeniedException(Perm.STAFF_PERMISSIONS);
            requireDelegable(me, Set.of(), permissions);
        }
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String fullName = request.fullName().trim();
        if (staffRepository.findByUsernameIgnoreCase(username).isPresent()) {
            throw new DuplicateResourceException("Someone on the staff list already uses the username " + username + ".");
        }
        if (staffRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new DuplicateResourceException("Someone on the staff list already uses " + email + ".");
        }

        String keycloakId = keycloak.createUser(new KeycloakAdminClient.NewLogin(username, email, fullName),
                request.temporaryPassword());
        try {
            keycloak.setStaffRole(keycloakId, role);
            StaffMember member = new StaffMember();
            member.setKeycloakUserId(keycloakId);
            member.setUsername(username);
            member.setEmail(email);
            member.setFullName(fullName);
            member.setRole(role);
            member.setActive(true);
            member.setCreatedBy(me.username());
            member.setPermissions(new HashSet<>(permissions));
            member = staffRepository.saveAndFlush(member);
            StaffAccess.describe("Added " + roleName(role) + " " + member.displayName() + " (" + username + ")");
            return toDTO(member, me);
        } catch (RuntimeException e) {
            // Don't leave a login behind that the staff list doesn't know about.
            keycloak.deleteUserQuietly(keycloakId);
            throw e;
        }
    }

    @Transactional
    public StaffDTO update(UUID id, StaffUpdateRequest request) {
        StaffView me = actor();
        StaffMember target = find(id);
        requireMayManage(me, target);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String fullName = request.fullName().trim();
        staffRepository.findByEmailIgnoreCase(email)
                .filter(other -> !other.getId().equals(target.getId()))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("Someone on the staff list already uses " + email + ".");
                });
        keycloak.updateProfile(target.getKeycloakUserId(), email, fullName);
        target.setEmail(email);
        target.setFullName(fullName);
        staffRepository.save(target);
        evictAfterCommit(target);
        StaffAccess.describe("Edited the details of " + target.displayName());
        return toDTO(target, me);
    }

    /** Admins only (see the controller). Starting a new role clears the old permissions. */
    @Transactional
    public StaffDTO changeRole(UUID id, StaffMember.Role role) {
        StaffView me = actor();
        StaffMember target = find(id);
        if (target.getRole() == role) return toDTO(target, me);
        if (isSelf(me, target)) throw new InvalidRequestException("You can't change your own role. Ask another admin.");
        if (target.isAdmin()) requireAnotherActiveAdmin(target);
        keycloak.setStaffRole(target.getKeycloakUserId(), role);
        target.setRole(role);
        target.getPermissions().clear();
        staffRepository.save(target);
        evictAfterCommit(target);
        StaffAccess.describe(role == StaffMember.Role.ADMIN
                ? "Made " + target.displayName() + " an admin"
                : "Made " + target.displayName() + " a moderator");
        return toDTO(target, me);
    }

    @Transactional
    public StaffDTO setPermissions(UUID id, Collection<String> requested) {
        StaffView me = actor();
        StaffMember target = find(id);
        if (target.isAdmin()) {
            throw new InvalidRequestException("Admins can do everything, so there are no permissions to give them.");
        }
        if (isSelf(me, target)) throw new InvalidRequestException("You can't change your own permissions. Ask an admin.");
        Set<String> after = known(requested);
        Set<String> before = Set.copyOf(target.getPermissions());
        requireDelegable(me, before, after);
        target.getPermissions().clear();
        target.getPermissions().addAll(after);
        staffRepository.save(target);
        evictAfterCommit(target);
        StaffAccess.describe("Changed the permissions of " + target.displayName() + ": " + changes(before, after));
        return toDTO(target, me);
    }

    @Transactional
    public StaffDTO deactivate(UUID id) {
        StaffView me = actor();
        StaffMember target = find(id);
        if (isSelf(me, target)) throw new InvalidRequestException("You can't deactivate your own account.");
        requireMayManage(me, target);
        if (!target.isActive()) return toDTO(target, me);
        if (target.isAdmin()) requireAnotherActiveAdmin(target);
        keycloak.setEnabled(target.getKeycloakUserId(), false);
        keycloak.signOutEverywhere(target.getKeycloakUserId());
        target.setActive(false);
        staffRepository.save(target);
        evictAfterCommit(target);
        StaffAccess.describe("Deactivated " + target.displayName() + " and signed them out");
        return toDTO(target, me);
    }

    @Transactional
    public StaffDTO reactivate(UUID id) {
        StaffView me = actor();
        StaffMember target = find(id);
        requireMayManage(me, target);
        if (target.isActive()) return toDTO(target, me);
        keycloak.setEnabled(target.getKeycloakUserId(), true);
        target.setActive(true);
        staffRepository.save(target);
        evictAfterCommit(target);
        StaffAccess.describe("Reactivated " + target.displayName());
        return toDTO(target, me);
    }

    @Transactional
    public void resetPassword(UUID id, String temporaryPassword) {
        StaffView me = actor();
        StaffMember target = find(id);
        if (isSelf(me, target)) {
            throw new InvalidRequestException("Change your own password from your account page, not here.");
        }
        requireMayManage(me, target);
        keycloak.setTemporaryPassword(target.getKeycloakUserId(), temporaryPassword);
        keycloak.signOutEverywhere(target.getKeycloakUserId());
        StaffAccess.describe("Reset the password of " + target.displayName()
                + " to a temporary one and signed them out");
    }

    /** Removes the login and the permissions for good. Their activity history stays, by name. */
    @Transactional
    public void delete(UUID id, String confirmation) {
        StaffView me = actor();
        StaffMember target = find(id);
        if (isSelf(me, target)) throw new InvalidRequestException("You can't delete your own account.");
        requireMayManage(me, target);
        if (target.isAdmin()) requireAnotherActiveAdmin(target);
        if (confirmation == null || !confirmation.trim().equalsIgnoreCase(target.getUsername())) {
            throw new InvalidRequestException("To delete " + target.displayName() + ", type their username ("
                    + target.getUsername() + ") to confirm. This can't be undone.");
        }
        keycloak.deleteUser(target.getKeycloakUserId());
        staffRepository.delete(target);
        evictAfterCommit(target);
        StaffAccess.describe("Deleted " + target.displayName() + " (" + target.getUsername() + ") for good");
    }

    // ── templates ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PermissionTemplateDTO> templates() {
        return templateRepository.findAllByOrderByNameAsc().stream().map(this::toDTO).toList();
    }

    @Transactional
    public PermissionTemplateDTO createTemplate(PermissionTemplateRequest request) {
        StaffView me = actor();
        String name = request.name().trim();
        templateRepository.findByNameIgnoreCase(name).ifPresent(t -> {
            throw new DuplicateResourceException("A template called \"" + name + "\" already exists.");
        });
        Set<String> permissions = known(request.permissions());
        requireDelegable(me, Set.of(), permissions);
        PermissionTemplate template = new PermissionTemplate();
        template.setName(name);
        template.setCreatedBy(me.username());
        template.setPermissions(new HashSet<>(permissions));
        template = templateRepository.save(template);
        StaffAccess.describe("Created the permission template \"" + name + "\"");
        return toDTO(template);
    }

    @Transactional
    public PermissionTemplateDTO updateTemplate(UUID id, PermissionTemplateRequest request) {
        StaffView me = actor();
        PermissionTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("That permission template no longer exists."));
        String name = request.name().trim();
        templateRepository.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("A template called \"" + name + "\" already exists.");
                });
        Set<String> after = known(request.permissions());
        requireDelegable(me, Set.copyOf(template.getPermissions()), after);
        String before = template.getName();
        template.setName(name);
        template.getPermissions().clear();
        template.getPermissions().addAll(after);
        templateRepository.save(template);
        StaffAccess.describe(before.equals(name)
                ? "Changed the permission template \"" + name + "\""
                : "Renamed the permission template \"" + before + "\" to \"" + name + "\"");
        return toDTO(template);
    }

    @Transactional
    public void deleteTemplate(UUID id) {
        PermissionTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("That permission template no longer exists."));
        templateRepository.delete(template);
        StaffAccess.describe("Deleted the permission template \"" + template.getName() + "\"");
    }

    // ── rules ───────────────────────────────────────────────────────────────

    private static StaffView actor() {
        return StaffAccess.current().orElseThrow(() -> new PermissionDeniedException("Not a staff login."));
    }

    private StaffMember find(UUID id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("That person is no longer on the staff list."));
    }

    private static boolean isSelf(StaffView me, StaffMember target) {
        return target.getId().equals(me.id());
    }

    private static void requireMayManage(StaffView me, StaffMember target) {
        if (target.isAdmin() && !me.isAdmin()) {
            throw new PermissionDeniedException("Only an admin can change an admin's account.");
        }
    }

    private void requireAnotherActiveAdmin(StaffMember target) {
        if (target.isActive() && staffRepository.countByRoleAndActiveTrue(StaffMember.Role.ADMIN) <= 1) {
            throw new InvalidRequestException(target.displayName()
                    + " is the only active admin. Make someone else an admin first.");
        }
    }

    /** A moderator may only give or take away permissions they hold. Admins may change anything. */
    private static void requireDelegable(StaffView me, Set<String> before, Set<String> after) {
        if (me.isAdmin()) return;
        Set<String> changed = new TreeSet<>(after);
        changed.removeAll(before);
        Set<String> removed = new TreeSet<>(before);
        removed.removeAll(after);
        changed.addAll(removed);
        List<String> notHeld = changed.stream().filter(k -> !me.permissions().contains(k)).toList();
        if (!notHeld.isEmpty()) {
            throw new PermissionDeniedException("You can only give or take away permissions you hold yourself. Not yours: "
                    + notHeld.stream().map(StaffManagementService::label).collect(Collectors.joining(", ")) + ".");
        }
    }

    /** The requested keys, checked against the catalogue. */
    private static Set<String> known(Collection<String> keys) {
        Set<String> result = new TreeSet<>();
        if (keys == null) return result;
        for (String key : keys) {
            String k = key == null ? "" : key.trim();
            if (!PERMS.containsKey(k)) throw new InvalidRequestException("Unknown permission: " + k);
            result.add(k);
        }
        return result;
    }

    /** "+Cancel orders, −See coupons" for the activity log. */
    private static String changes(Set<String> before, Set<String> after) {
        List<String> parts = new java.util.ArrayList<>();
        after.stream().filter(k -> !before.contains(k)).sorted().forEach(k -> parts.add("+" + label(k)));
        before.stream().filter(k -> !after.contains(k)).sorted().forEach(k -> parts.add("−" + label(k)));
        return parts.isEmpty() ? "no change" : String.join(", ", parts);
    }

    private static String label(String key) {
        Perm perm = PERMS.get(key);
        return perm == null ? key : perm.label();
    }

    private static String roleName(StaffMember.Role role) {
        return role == StaffMember.Role.ADMIN ? "admin" : "moderator";
    }

    /** Clears the person's cached access once the change is committed, never before. */
    private void evictAfterCommit(StaffMember target) {
        String keycloakId = target.getKeycloakUserId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    directory.evict(keycloakId);
                }
            });
        } else {
            directory.evict(keycloakId);
        }
    }

    // ── mapping ─────────────────────────────────────────────────────────────

    private StaffDTO toDTO(StaffMember m, StaffView me) {
        return new StaffDTO(m.getId(), m.getUsername(), m.getEmail(), m.getFullName(), m.displayName(),
                m.getRole().name(), m.isActive(), shown(m.getLastSeenAt()), shown(m.getCreatedAt()), m.getCreatedBy(),
                m.isAdmin() ? List.of() : m.getPermissions().stream().sorted().toList(),
                m.getId() != null && m.getId().equals(me.id()));
    }

    private PermissionTemplateDTO toDTO(PermissionTemplate t) {
        return new PermissionTemplateDTO(t.getId(), t.getName(), t.getPermissions().stream().sorted().toList(),
                t.getCreatedBy(), shown(t.getUpdatedAt()));
    }

    private String shown(LocalDateTime utc) {
        return utc == null ? null : SHOWN.format(calendar.fromUtc(utc));
    }
}
