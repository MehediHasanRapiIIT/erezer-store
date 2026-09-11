package kn.org.deliverybackend.access;

import kn.org.deliverybackend.dto.staff.StaffCreateRequest;
import kn.org.deliverybackend.entity.StaffMember;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.PermissionDeniedException;
import kn.org.deliverybackend.integration.keycloak.KeycloakAdminClient;
import kn.org.deliverybackend.reporting.BusinessCalendar;
import kn.org.deliverybackend.repository.PermissionTemplateRepository;
import kn.org.deliverybackend.repository.StaffMemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The Staff page's safety rules, with Keycloak and the database mocked. */
@ExtendWith(MockitoExtension.class)
class StaffManagementServiceTest {

    @Mock private StaffMemberRepository staffRepository;
    @Mock private PermissionTemplateRepository templateRepository;
    @Mock private KeycloakAdminClient keycloak;
    @Mock private StaffDirectory directory;

    private StaffManagementService service;
    private StaffMember admin;
    private StaffMember secondAdmin;
    private StaffMember mod;
    private StaffMember rahim;

    @BeforeEach
    void setUp() {
        service = new StaffManagementService(staffRepository, templateRepository, keycloak, directory,
                new BusinessCalendar(ZoneId.of("Asia/Dhaka"), DayOfWeek.SUNDAY, 7));
        admin = member("kc-admin", "admin", StaffMember.Role.ADMIN);
        secondAdmin = member("kc-admin2", "nasrin", StaffMember.Role.ADMIN);
        mod = member("kc-mod", "mod", StaffMember.Role.MODERATOR, "orders.view", "staff.view", "staff.manage", "staff.permissions");
        rahim = member("kc-rahim", "rahim", StaffMember.Role.MODERATOR, "coupons.view");
        for (StaffMember m : List.of(admin, secondAdmin, mod, rahim)) {
            lenient().when(staffRepository.findById(m.getId())).thenReturn(Optional.of(m));
        }
        lenient().when(staffRepository.countByRoleAndActiveTrue(StaffMember.Role.ADMIN)).thenReturn(2L);
        lenient().when(staffRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(staffRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ── own account ─────────────────────────────────────────────────────────

    @Test
    void nobodyDeactivatesDeletesDemotesOrReconfiguresThemselves() {
        actingAs(admin);
        assertThrows(InvalidRequestException.class, () -> service.deactivate(admin.getId()));
        assertThrows(InvalidRequestException.class, () -> service.delete(admin.getId(), "admin"));
        assertThrows(InvalidRequestException.class, () -> service.changeRole(admin.getId(), StaffMember.Role.MODERATOR));
        assertThrows(InvalidRequestException.class, () -> service.resetPassword(admin.getId(), "temporary-123"));

        actingAs(mod);
        assertThrows(InvalidRequestException.class, () -> service.setPermissions(mod.getId(), List.of("orders.view", "coupons.view")));
        verify(keycloak, never()).setEnabled(anyString(), anyBoolean());
        verify(keycloak, never()).deleteUser(anyString());
    }

    // ── last admin ──────────────────────────────────────────────────────────

    @Test
    void theOnlyActiveAdminStays() {
        when(staffRepository.countByRoleAndActiveTrue(StaffMember.Role.ADMIN)).thenReturn(1L);
        actingAs(secondAdmin);
        InvalidRequestException refused = assertThrows(InvalidRequestException.class, () -> service.deactivate(admin.getId()));
        assertTrue(refused.getMessage().contains("only active admin"));
        assertThrows(InvalidRequestException.class, () -> service.delete(admin.getId(), "admin"));
        assertThrows(InvalidRequestException.class, () -> service.changeRole(admin.getId(), StaffMember.Role.MODERATOR));
        verify(keycloak, never()).setEnabled(anyString(), anyBoolean());
    }

    @Test
    void anotherAdminCanBeDeactivatedWhileOneRemains() {
        actingAs(secondAdmin);
        service.deactivate(admin.getId());
        verify(keycloak).setEnabled("kc-admin", false);
        verify(keycloak).signOutEverywhere("kc-admin");
        verify(directory).evict("kc-admin");
    }

    // ── admins are for admins ───────────────────────────────────────────────

    @Test
    void aModeratorCannotTouchAnAdmin() {
        actingAs(mod);
        assertThrows(PermissionDeniedException.class, () -> service.deactivate(admin.getId()));
        assertThrows(PermissionDeniedException.class, () -> service.resetPassword(admin.getId(), "temporary-123"));
        assertThrows(PermissionDeniedException.class, () -> service.delete(admin.getId(), "admin"));
        verify(keycloak, never()).setTemporaryPassword(anyString(), anyString());
    }

    @Test
    void onlyAnAdminAddsAnAdmin() {
        actingAs(mod);
        assertThrows(PermissionDeniedException.class, () -> service.create(new StaffCreateRequest(
                "New Boss", "boss", "boss@erezer.test", "temporary-123", StaffMember.Role.ADMIN, List.of())));
        verify(keycloak, never()).createUser(any(), anyString());
    }

    // ── delegation ──────────────────────────────────────────────────────────

    @Test
    void aModeratorGivesAndTakesOnlyWhatTheyHold() {
        actingAs(mod);
        // Adds orders.view (held); coupons.view stays as it was, so it isn't a change.
        assertDoesNotThrow(() -> service.setPermissions(rahim.getId(), List.of("coupons.view", "orders.view")));
        assertEquals(Set.of("coupons.view", "orders.view"), rahim.getPermissions());

        PermissionDeniedException giving = assertThrows(PermissionDeniedException.class,
                () -> service.setPermissions(rahim.getId(), List.of("coupons.view", "orders.view", "finance.revenue")));
        assertTrue(giving.getMessage().contains("See money totals"));

        // Taking away coupons.view, which the moderator doesn't hold, is refused too.
        assertThrows(PermissionDeniedException.class, () -> service.setPermissions(rahim.getId(), List.of("orders.view")));
        assertEquals(Set.of("coupons.view", "orders.view"), rahim.getPermissions());
    }

    @Test
    void unknownPermissionsAndAdminsAreRejected() {
        actingAs(admin);
        assertThrows(InvalidRequestException.class, () -> service.setPermissions(rahim.getId(), List.of("orders.fly")));
        assertThrows(InvalidRequestException.class, () -> service.setPermissions(secondAdmin.getId(), List.of("orders.view")));
    }

    @Test
    void aPermissionChangeClearsTheCachedAccess() {
        actingAs(admin);
        service.setPermissions(rahim.getId(), List.of("orders.view"));
        verify(directory).evict("kc-rahim");
    }

    // ── deleting ────────────────────────────────────────────────────────────

    @Test
    void deletingNeedsTheUsernameTyped() {
        actingAs(admin);
        assertThrows(InvalidRequestException.class, () -> service.delete(rahim.getId(), "rahi"));
        assertThrows(InvalidRequestException.class, () -> service.delete(rahim.getId(), null));
        verify(keycloak, never()).deleteUser(anyString());

        service.delete(rahim.getId(), " RAHIM ");
        verify(keycloak).deleteUser("kc-rahim");
        verify(staffRepository).delete(rahim);
        verify(directory).evict("kc-rahim");
    }

    // ── adding ──────────────────────────────────────────────────────────────

    @Test
    void aFailedSaveRemovesTheNewLogin() {
        actingAs(admin);
        when(keycloak.createUser(any(), anyString())).thenReturn("kc-new");
        when(staffRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        assertThrows(DataIntegrityViolationException.class, () -> service.create(new StaffCreateRequest(
                "Karim Uddin", "karim", "karim@erezer.test", "temporary-123", null, List.of("orders.view"))));
        verify(keycloak).deleteUserQuietly("kc-new");
    }

    @Test
    void aNewModeratorGetsTheirLoginRoleAndPermissions() {
        actingAs(admin);
        when(keycloak.createUser(any(), anyString())).thenReturn("kc-new");
        var created = service.create(new StaffCreateRequest(
                "Karim Uddin", " Karim ", "Karim@Erezer.test", "temporary-123", null, List.of("orders.view")));
        verify(keycloak).setStaffRole("kc-new", StaffMember.Role.MODERATOR);
        assertEquals("karim", created.username());
        assertEquals("karim@erezer.test", created.email());
        assertEquals(List.of("orders.view"), created.permissions());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void actingAs(StaffMember member) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(StaffAccess.STAFF_ATTRIBUTE, StaffDirectory.toView(member));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static StaffMember member(String keycloakId, String username, StaffMember.Role role, String... permissions) {
        StaffMember m = new StaffMember();
        m.setId(UUID.randomUUID());
        m.setKeycloakUserId(keycloakId);
        m.setUsername(username);
        m.setFullName(username.substring(0, 1).toUpperCase() + username.substring(1));
        m.setRole(role);
        m.setActive(true);
        m.setPermissions(new HashSet<>(Set.of(permissions)));
        return m;
    }
}
