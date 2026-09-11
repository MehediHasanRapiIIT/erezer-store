package kn.org.deliverybackend.access;

import kn.org.deliverybackend.entity.StaffMember;
import kn.org.deliverybackend.exception.PermissionDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deny by default. Every endpoint a staff login can reach must say who may use
 * it, so a new admin feature cannot ship open to every moderator by accident.
 * An endpoint with no rule is treated as admin-only at runtime; this test
 * makes the build fail so the choice is made on purpose.
 */
class AdminEndpointCoverageTest {

    /** Catalogue paths whose POST, PUT and DELETE go to the staff security chain. */
    private static final List<String> STAFF_CATALOGUE = List.of("/api/products", "/api/categories", "/api/banners");
    private static final Set<RequestMethod> STAFF_CHAIN_VERBS = Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE);
    private static final Set<RequestMethod> CHANGES = Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);
    /** Written by customers with their own login; kept out of the staff chain on purpose. */
    private static final Pattern CUSTOMER_REVIEWS = Pattern.compile("^/api/products/[^/]+/reviews(/.*)?$");

    private record Endpoint(RequestMethod verb, String path, Class<?> type, Method method) {
        @Override
        public String toString() {
            return verb + " " + path + "  (" + type.getSimpleName() + "." + method.getName() + ")";
        }
    }

    @Test
    void everyStaffEndpointSaysWhoMayUseIt() throws Exception {
        List<Endpoint> staffEndpoints = endpoints().stream().filter(AdminEndpointCoverageTest::staffChain).toList();
        List<String> undeclared = staffEndpoints.stream()
                .filter(e -> !AccessRule.isDeclared(e.method(), e.type()))
                .map(Endpoint::toString)
                .toList();

        assertTrue(staffEndpoints.size() > 90,
                "Only " + staffEndpoints.size() + " staff endpoints found; the scan is probably broken.");
        assertTrue(undeclared.isEmpty(), "Staff endpoints with no @RequiresPermission, @AdminOnly or @AnyStaff:\n  "
                + String.join("\n  ", undeclared));
    }

    @Test
    void noCatalogueChangeFallsThroughToThePublicChain() throws Exception {
        // PATCH (or any verb not listed in SecurityConfig) on these paths would
        // land on the storefront chain, where /api/products/** is public.
        List<String> open = endpoints().stream()
                .filter(e -> CHANGES.contains(e.verb()) && !STAFF_CHAIN_VERBS.contains(e.verb()))
                .filter(e -> STAFF_CATALOGUE.stream().anyMatch(e.path()::startsWith))
                .filter(e -> !CUSTOMER_REVIEWS.matcher(e.path()).matches())
                .map(Endpoint::toString)
                .toList();
        assertTrue(open.isEmpty(), "Catalogue changes not covered by the staff chain:\n  " + String.join("\n  ", open));
    }

    // ── the rule itself ─────────────────────────────────────────────────────

    static class Sample {
        public void unmarked() { }
        @RequiresPermission(Perm.ORDERS_VIEW) public void view() { }
        @RequiresPermission(value = {Perm.ORDERS_STATUS, Perm.ORDERS_CANCEL}, mode = RequiresPermission.Mode.ANY)
        public void either() { }
        @RequiresPermission({Perm.REPORTS_VIEW, Perm.FINANCE_REVENUE}) public void both() { }
        @AnyStaff public void anyone() { }
        @AdminOnly public void adminsOnly() { }
    }

    @Test
    void anUnmarkedEndpointIsAdminOnly() throws Exception {
        AccessRule rule = rule("unmarked");
        assertEquals(AccessRule.Kind.ADMIN_ONLY, rule.kind());
        assertThrows(PermissionDeniedException.class, () -> rule.check(moderator(Perm.values())));
        assertThrows(PermissionDeniedException.class, () -> rule("adminsOnly").check(moderator(Perm.values())));
    }

    @Test
    void permissionModes() throws Exception {
        assertDoesNotThrow(() -> rule("view").check(moderator(Perm.ORDERS_VIEW)));
        PermissionDeniedException refused = assertThrows(PermissionDeniedException.class,
                () -> rule("view").check(moderator()));
        assertEquals(List.of("orders.view"), refused.getMissing());

        assertDoesNotThrow(() -> rule("either").check(moderator(Perm.ORDERS_CANCEL)));
        assertThrows(PermissionDeniedException.class, () -> rule("either").check(moderator(Perm.ORDERS_VIEW)));

        PermissionDeniedException half = assertThrows(PermissionDeniedException.class,
                () -> rule("both").check(moderator(Perm.REPORTS_VIEW)));
        assertEquals(List.of("finance.revenue"), half.getMissing());

        assertDoesNotThrow(() -> rule("anyone").check(moderator()));
    }

    @Test
    void permissionKeysAreUnique() {
        Set<String> keys = new java.util.HashSet<>();
        for (Perm p : Perm.values()) keys.add(p.key());
        assertEquals(Perm.values().length, keys.size());
    }

    // ── scanning ────────────────────────────────────────────────────────────

    private static boolean staffChain(Endpoint e) {
        if (e.path().startsWith("/admin") || e.path().startsWith("/ws")) return true;
        return STAFF_CHAIN_VERBS.contains(e.verb())
                && STAFF_CATALOGUE.stream().anyMatch(e.path()::startsWith)
                && !CUSTOMER_REVIEWS.matcher(e.path()).matches();
    }

    private static List<Endpoint> endpoints() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Endpoint> found = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("kn.org.deliverybackend")) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            RequestMapping onType = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String[] prefixes = onType == null || onType.path().length == 0 ? new String[]{""} : onType.path();
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                String[] paths = mapping.path().length == 0 ? new String[]{""} : mapping.path();
                RequestMethod[] verbs = mapping.method().length == 0 ? RequestMethod.values() : mapping.method();
                for (String prefix : prefixes)
                    for (String path : paths)
                        for (RequestMethod verb : verbs)
                            found.add(new Endpoint(verb, prefix + path, type, method));
            }
        }
        return found;
    }

    private static AccessRule rule(String method) throws NoSuchMethodException {
        return AccessRule.of(new HandlerMethod(new Sample(), method));
    }

    private static StaffView moderator(Perm... perms) {
        Set<String> keys = new java.util.HashSet<>();
        for (Perm p : perms) keys.add(p.key());
        return new StaffView(UUID.randomUUID(), "kc", "mod", null, "Mod", StaffMember.Role.MODERATOR, true, Set.copyOf(keys));
    }
}
