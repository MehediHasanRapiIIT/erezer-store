package kn.org.deliverybackend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import kn.org.deliverybackend.access.ActivityService;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.StaffDirectory;
import kn.org.deliverybackend.config.CorsConfig;
import kn.org.deliverybackend.config.SecurityConfig;
import kn.org.deliverybackend.controller.AdminCouponController;
import kn.org.deliverybackend.controller.AdminCustomerController;
import kn.org.deliverybackend.controller.AdminMeController;
import kn.org.deliverybackend.controller.AdminOrderController;
import kn.org.deliverybackend.controller.AdminStoreSettingsController;
import kn.org.deliverybackend.dto.report.CustomerLifetimeValueDTO;
import kn.org.deliverybackend.dto.settings.StoreSettingsDTO;
import kn.org.deliverybackend.entity.StaffMember;
import kn.org.deliverybackend.repository.AdminActivityRepository;
import kn.org.deliverybackend.repository.StaffMemberRepository;
import kn.org.deliverybackend.service.CouponService;
import kn.org.deliverybackend.service.InvoiceService;
import kn.org.deliverybackend.service.OrderHistoryService;
import kn.org.deliverybackend.service.OrderService;
import kn.org.deliverybackend.service.ReportService;
import kn.org.deliverybackend.service.StoreSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Staff permissions, exercised through the real security chain and permission
 * check with no database: Keycloak tokens are faked by the decoder, staff rows
 * live in a map behind a mocked repository, services are mocked.
 */
@WebMvcTest(controllers = {AdminCouponController.class, AdminOrderController.class, AdminMeController.class,
        AdminCustomerController.class, AdminStoreSettingsController.class,
        kn.org.deliverybackend.controller.CategoryController.class})
@Import({SecurityConfig.class, JwtTokenProvider.class, CorsConfig.class, StaffDirectory.class, ActivityService.class})
@TestPropertySource(properties = {
        "app.jwt.secret=" + CustomerAccessSecurityTest.SECRET,
        "app.jwt.issuer=erezer-store",
})
class AdminPermissionSecurityTest {

    private static final UUID COUPON = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID ORDER = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Autowired private MockMvc mvc;
    @Autowired private StaffDirectory directory;
    @Autowired private ObjectMapper json;

    @MockBean private JwtDecoder jwtDecoder;
    @MockBean private StaffMemberRepository staffRepository;
    @MockBean private AdminActivityRepository activityRepository;
    @MockBean private PlatformTransactionManager transactionManager;
    @MockBean private CouponService couponService;
    @MockBean private OrderHistoryService orderHistoryService;
    @MockBean private OrderService orderService;
    @MockBean private InvoiceService invoiceService;
    @MockBean private ReportService reportService;
    @MockBean private StoreSettingsService storeSettingsService;
    @MockBean private kn.org.deliverybackend.integration.keycloak.KeycloakAdminClient keycloak;

    /** Staff rows by Keycloak user id: the fake database. */
    private final Map<String, StaffMember> staff = new HashMap<>();

    @BeforeEach
    void setUp() {
        directory.evictAll();
        staff.clear();
        when(keycloak.loginExists(anyString())).thenReturn(true);

        when(jwtDecoder.decode(anyString())).thenAnswer(inv -> switch ((String) inv.getArgument(0)) {
            case "admin" -> token("admin", "kc-admin", "admin");
            case "mod" -> token("mod", "kc-mod", "moderator");
            case "newmod" -> token("newmod", "kc-newmod", "moderator");
            case "outsider" -> token("outsider", "kc-outsider");
            default -> throw new BadJwtException("unknown test token");
        });
        when(staffRepository.findByKeycloakUserId(anyString()))
                .thenAnswer(inv -> java.util.Optional.ofNullable(staff.get((String) inv.getArgument(0))));
        when(staffRepository.saveAndFlush(any())).thenAnswer(inv -> store(inv.getArgument(0)));
        when(staffRepository.save(any())).thenAnswer(inv -> store(inv.getArgument(0)));
    }

    // ── who gets in ─────────────────────────────────────────────────────────

    @Test
    void anAdminMayDoEverything() throws Exception {
        as("admin", get("/admin/coupons")).andExpect(status().isOk());
        as("admin", delete("/admin/coupons/" + COUPON)).andExpect(status().is2xxSuccessful());
        assertEquals(StaffMember.Role.ADMIN, staff.get("kc-admin").getRole(), "admin row created on first login");
    }

    @Test
    void noLoginIsTreatedAsLoggedOut() throws Exception {
        mvc.perform(get("/admin/coupons")).andExpect(status().isUnauthorized());
    }

    @Test
    void aKeycloakUserWithNoStaffRoleIsRefusedAndNotRecorded() throws Exception {
        as("outsider", get("/admin/me")).andExpect(status().isForbidden());
        verify(staffRepository, never()).saveAndFlush(any());
    }

    @Test
    void aNewModeratorStartsWithNoPermissions() throws Exception {
        as("newmod", get("/admin/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"))
                .andExpect(jsonPath("$.admin").value(false))
                .andExpect(jsonPath("$.permissions.length()").value(0));
        as("newmod", get("/admin/coupons")).andExpect(status().isForbidden());
        assertEquals(StaffMember.Role.MODERATOR, staff.get("kc-newmod").getRole());
    }

    @Test
    void anAdminIsToldTheyHoldEveryPermission() throws Exception {
        as("admin", get("/admin/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true))
                .andExpect(jsonPath("$.permissions.length()").value(Perm.values().length));
    }

    @Test
    void aDeactivatedModeratorIsRefusedEvenWithPermissions() throws Exception {
        moderator("coupons.view").setActive(false);
        as("mod", get("/admin/coupons")).andExpect(status().isForbidden());
    }

    // ── per-action permissions ──────────────────────────────────────────────

    @Test
    void aRefusalNamesTheMissingPermission() throws Exception {
        moderator();
        as("mod", get("/admin/coupons"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.missingPermissions[0]").value("coupons.view"));
    }

    @Test
    void seeingIsNotChanging() throws Exception {
        moderator("coupons.view");
        as("mod", get("/admin/coupons")).andExpect(status().isOk());
        as("mod", delete("/admin/coupons/" + COUPON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.missingPermissions[0]").value("coupons.delete"));
        verify(couponService, never()).delete(any());
    }

    @Test
    void aGrantAndARevokeApplyToTheVeryNextRequest() throws Exception {
        StaffMember mod = moderator();
        as("mod", get("/admin/coupons")).andExpect(status().isForbidden());

        mod.getPermissions().add("coupons.view");
        directory.evict("kc-mod");
        as("mod", get("/admin/coupons")).andExpect(status().isOk());

        mod.getPermissions().remove("coupons.view");
        directory.evict("kc-mod");
        as("mod", get("/admin/coupons")).andExpect(status().isForbidden());
    }

    @Test
    void cancellingAnOrderIsItsOwnPermission() throws Exception {
        moderator("orders.status");
        as("mod", orderStatus(ORDER, "PROCESSING")).andExpect(status().isOk());
        as("mod", orderStatus(ORDER, "CANCELLED"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.missingPermissions[0]").value("orders.cancel"));

        staff.clear();
        directory.evictAll();
        moderator("orders.cancel");
        as("mod", orderStatus(ORDER, "CANCELLED")).andExpect(status().isOk());
        as("mod", orderStatus(ORDER, "PROCESSING"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.missingPermissions[0]").value("orders.status"));
    }

    // ── money totals ────────────────────────────────────────────────────────

    @Test
    void customerLifetimeValueNeedsMoneyTotals() throws Exception {
        when(reportService.customerLtv(50, 0, null)).thenAnswer(inv -> List.of(CustomerLifetimeValueDTO.builder()
                .userId(UUID.randomUUID()).customerName("Rahim").orderCount(3)
                .lifetimeRevenue(new BigDecimal("5400.00")).averageOrderValue(new BigDecimal("1800.00")).build()));

        moderator("customers.view");
        as("mod", get("/admin/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderCount").value(3))
                .andExpect(jsonPath("$[0].lifetimeRevenue").doesNotExist())
                .andExpect(jsonPath("$[0].averageOrderValue").doesNotExist());

        staff.get("kc-mod").getPermissions().add("finance.revenue");
        directory.evict("kc-mod");
        as("mod", get("/admin/customers"))
                .andExpect(jsonPath("$[0].lifetimeRevenue").value(5400.00));
    }

    // ── settings sections ───────────────────────────────────────────────────

    @Test
    void eachSettingsSectionNeedsItsOwnPermission() throws Exception {
        StoreSettingsDTO current = StoreSettingsDTO.builder()
                .supportPhone("01700000000").supportEmail("help@erezer.test")
                .paymentCodEnabled(true).paymentBkashEnabled(true).paymentCardEnabled(true).build();
        when(storeSettingsService.get()).thenReturn(current);
        when(storeSettingsService.update(any())).thenAnswer(inv -> inv.getArgument(0));
        moderator("settings.payments");

        StoreSettingsDTO codOff = copy(current);
        codOff.setPaymentCodEnabled(false);
        as("mod", put("/admin/store-settings").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(codOff)))
                .andExpect(status().isOk());

        StoreSettingsDTO newPhone = copy(current);
        newPhone.setSupportPhone("01800000000");
        as("mod", put("/admin/store-settings").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(newPhone)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.missingPermissions[0]").value("settings.store"));
        verify(storeSettingsService, times(1)).update(any());
    }

    // ── activity log ────────────────────────────────────────────────────────

    @Test
    void onlySuccessfulChangesAreLogged() throws Exception {
        moderator("coupons.view", "coupons.delete");
        as("mod", get("/admin/coupons")).andExpect(status().isOk());
        as("mod", delete("/admin/coupons/" + COUPON)).andExpect(status().is2xxSuccessful());
        as("mod", orderStatus(ORDER, "CANCELLED")).andExpect(status().isForbidden());

        verify(activityRepository, times(1)).save(any());
        verify(activityRepository).save(argThat(a -> "coupons.delete".equals(a.getPermKey())
                && "DELETE".equals(a.getMethod())
                && COUPON.toString().equals(a.getTargetId())
                && "Moderator One".equals(a.getStaffName())));
    }

    @Test
    void theLogNamesThePermissionActuallyUsed() throws Exception {
        moderator("orders.cancel");
        as("mod", orderStatus(ORDER, "CANCELLED")).andExpect(status().isOk());
        verify(activityRepository).save(argThat(a -> "orders.cancel".equals(a.getPermKey())
                && "Changed order status to CANCELLED".equals(a.getSummary())
                && ORDER.toString().equals(a.getTargetId())));
    }

    // ── categories: "Never discount" ────────────────────────────────────────

    @MockBean private kn.org.deliverybackend.service.CategoryService categoryService;
    @MockBean private kn.org.deliverybackend.service.ProductService productService;

    @Test
    void leavingOutNeverDiscountIsNotAWayRoundThePermission() throws Exception {
        var current = org.mockito.Mockito.mock(kn.org.deliverybackend.dto.response.category.CategoryResponseDTO.class);
        when(current.getDiscountExcluded()).thenReturn(true);
        when(categoryService.getCategoryById(5L)).thenReturn(current);
        moderator("categories.edit");

        // The field left out would be saved as off, so this is a change to it.
        as("mod", put("/api/categories/5").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tops\",\"isActive\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.missingPermissions[0]").value("discounts.switches"));
        verify(categoryService, never()).updateCategory(any(), any());

        // Sent back unchanged, it is not a change.
        as("mod", put("/api/categories/5").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tops\",\"isActive\":true,\"discountExcluded\":true}"))
                .andExpect(status().isOk());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private ResultActions as(String token, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder orderStatus(UUID order, String to) {
        return patch("/admin/orders/" + order + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + to + "\"}");
    }

    /** An existing moderator row for the "mod" login, holding exactly these permissions. */
    private StaffMember moderator(String... permissions) {
        StaffMember m = new StaffMember();
        m.setId(UUID.randomUUID());
        m.setKeycloakUserId("kc-mod");
        m.setUsername("mod");
        m.setFullName("Moderator One");
        m.setRole(StaffMember.Role.MODERATOR);
        m.setPermissions(new java.util.HashSet<>(Set.of(permissions)));
        staff.put(m.getKeycloakUserId(), m);
        return m;
    }

    private StaffMember store(StaffMember m) {
        if (m.getId() == null) m.setId(UUID.randomUUID());
        staff.put(m.getKeycloakUserId(), m);
        return m;
    }

    private StoreSettingsDTO copy(StoreSettingsDTO s) throws Exception {
        return json.readValue(json.writeValueAsString(s), StoreSettingsDTO.class);
    }

    private static Jwt token(String username, String subject, String... realmRoles) {
        return Jwt.withTokenValue(username)
                .header("alg", "none")
                .subject(subject)
                .claim("preferred_username", username)
                .claim("realm_access", Map.of("roles", List.of(realmRoles)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

}
