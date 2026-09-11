package kn.org.deliverybackend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kn.org.deliverybackend.access.ActivityService;
import kn.org.deliverybackend.access.StaffDirectory;
import kn.org.deliverybackend.config.CorsConfig;
import kn.org.deliverybackend.config.SecurityConfig;
import kn.org.deliverybackend.controller.OrderHistoryController;
import kn.org.deliverybackend.controller.ReviewController;
import kn.org.deliverybackend.dto.request.review.ReviewRequestDTO;
import kn.org.deliverybackend.dto.request.review.ReviewUpdateRequestDTO;
import kn.org.deliverybackend.service.OrderHistoryService;
import kn.org.deliverybackend.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Customer-side access rules, exercised through the real security chains with
 * no database: services are mocked, tokens are minted with the test secret.
 *
 * <p>Pins the two holes found in the 11 Sep 2026 audit: one customer reading
 * another's data by changing the id in the address, and reviews being
 * unreachable for customers while trusting an id from the request.
 */
@WebMvcTest(controllers = {OrderHistoryController.class, ReviewController.class})
@Import({SecurityConfig.class, JwtTokenProvider.class, CorsConfig.class})
@TestPropertySource(properties = {
        "app.jwt.secret=" + CustomerAccessSecurityTest.SECRET,
        "app.jwt.issuer=erezer-store",
})
class CustomerAccessSecurityTest {

    static final String SECRET = "test-only-secret-that-is-well-over-thirty-two-bytes";

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORDER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID REVIEW = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired private MockMvc mvc;

    @MockBean private OrderHistoryService orderHistoryService;
    @MockBean private ReviewService reviewService;
    /** The admin chain's Keycloak decoder; rejects everything, as no admin token is used here. */
    @MockBean private JwtDecoder jwtDecoder;
    /** The staff permission check sits in front of every controller; no staff login is used here. */
    @MockBean private StaffDirectory staffDirectory;
    @MockBean private ActivityService activityService;

    @BeforeEach
    void rejectAdminTokens() {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("not a Keycloak token"));
        when(reviewService.getReviews(anyLong(), any())).thenReturn(Page.empty());
    }

    // ── customer data isolation ─────────────────────────────────────────────

    @Test
    void customerReadsOwnOrders() throws Exception {
        mvc.perform(get("/app/consumer/{u}/orders", ALICE).header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk());
    }

    @Test
    void ownIdInCapitalLettersStillCounts() throws Exception {
        mvc.perform(get("/app/consumer/" + ALICE.toString().toUpperCase() + "/orders")
                        .header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotReadAnotherCustomersOrders() throws Exception {
        mvc.perform(get("/app/consumer/{u}/orders", BOB).header("Authorization", bearer(ALICE)))
                .andExpect(status().isForbidden());
        verify(orderHistoryService, never()).getOrderHistory(any());
    }

    @Test
    void customerReadsOwnOrderHistoryPage() throws Exception {
        // Also pins that "paged" is not taken for an order id.
        mvc.perform(get("/app/consumer/{u}/orders/paged", ALICE).header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk());
        verify(orderHistoryService).getOrderHistoryPage(ALICE, 0, 10);
    }

    @Test
    void customerCannotReadAnotherCustomersOrderHistoryPage() throws Exception {
        mvc.perform(get("/app/consumer/{u}/orders/paged", BOB).header("Authorization", bearer(ALICE)))
                .andExpect(status().isForbidden());
        verify(orderHistoryService, never()).getOrderHistoryPage(any(), anyInt(), anyInt());
    }

    @Test
    void customerCannotReadAnotherCustomersSingleOrder() throws Exception {
        mvc.perform(get("/app/consumer/{u}/orders/{o}", BOB, ORDER).header("Authorization", bearer(ALICE)))
                .andExpect(status().isForbidden());
        verify(orderHistoryService, never()).getOrderDetails(any(), any());
    }

    @Test
    void theRuleCoversCustomerEndpointsNotInThisTest() throws Exception {
        // Cart, addresses, profile, returns, drafts: blocked by the address rule
        // itself, before any controller is involved.
        for (String path : new String[]{"/cart", "/addresses", "/profile", "/returns", "/custom-design/drafts"}) {
            mvc.perform(get("/app/consumer/" + BOB + path).header("Authorization", bearer(ALICE)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/app/consumer/" + BOB + path).header("Authorization", bearer(ALICE))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void noLoginGets401SoTheStorefrontCanRefreshItsToken() throws Exception {
        mvc.perform(get("/app/consumer/{u}/orders", BOB))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestCheckoutIsStillOpen() throws Exception {
        // Security must let it through. The exact status after that is not the
        // point: OrderController is not loaded in this slice, and the global
        // handler reports a missing handler as 500.
        int status = mvc.perform(post("/app/consumer/guest/orders")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertNotEquals(401, status, "guest checkout must not need a login");
        org.junit.jupiter.api.Assertions.assertNotEquals(403, status, "guest checkout must not be forbidden");
    }

    // ── reviews ─────────────────────────────────────────────────────────────

    @Test
    void anyoneCanReadReviews() throws Exception {
        mvc.perform(get("/api/products/19/reviews")).andExpect(status().isOk());
    }

    @Test
    void customerCanPostAReviewAndTheAuthorIsTheLoginNotTheRequest() throws Exception {
        // Bob sends Alice's id in the body; the review must still be Bob's.
        String body = "{\"userId\":\"" + ALICE + "\",\"orderId\":\"" + ORDER + "\",\"rating\":5,\"comment\":\"good\"}";
        mvc.perform(post("/api/products/19/reviews").header("Authorization", bearer(BOB))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        ArgumentCaptor<ReviewRequestDTO> sent = ArgumentCaptor.forClass(ReviewRequestDTO.class);
        verify(reviewService).submitReview(eq(19L), sent.capture());
        assertEquals(BOB, sent.getValue().getUserId());
    }

    @Test
    void reviewNeedsNoUserIdInTheBody() throws Exception {
        String body = "{\"orderId\":\"" + ORDER + "\",\"rating\":4}";
        mvc.perform(post("/api/products/19/reviews").header("Authorization", bearer(BOB))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void editingAReviewUsesTheLogin() throws Exception {
        String body = "{\"userId\":\"" + ALICE + "\",\"rating\":3,\"comment\":\"meh\"}";
        mvc.perform(put("/api/products/19/reviews/{r}", REVIEW).header("Authorization", bearer(BOB))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<ReviewUpdateRequestDTO> sent = ArgumentCaptor.forClass(ReviewUpdateRequestDTO.class);
        verify(reviewService).updateReview(eq(19L), eq(REVIEW), sent.capture());
        assertEquals(BOB, sent.getValue().getUserId());
    }

    @Test
    void deletingAReviewIgnoresTheUserIdInTheAddress() throws Exception {
        mvc.perform(delete("/api/products/19/reviews/{r}", REVIEW).param("userId", ALICE.toString())
                        .header("Authorization", bearer(BOB)))
                .andExpect(status().isNoContent());
        verify(reviewService).deleteReview(19L, REVIEW, BOB);
    }

    @Test
    void writingAReviewNeedsALogin() throws Exception {
        String body = "{\"orderId\":\"" + ORDER + "\",\"rating\":5}";
        mvc.perform(post("/api/products/19/reviews").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/products/19/reviews/{r}", REVIEW))
                .andExpect(status().isUnauthorized());
        verify(reviewService, never()).submitReview(anyLong(), any());
    }

    // ── staff-only catalogue changes stay staff-only ────────────────────────

    @Test
    void aCustomerLoginCannotCreateProducts() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", bearer(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/products/19").header("Authorization", bearer(ALICE)))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** A customer access token, signed the same way JwtTokenProvider signs them. */
    private static String bearer(UUID userId) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .issuer("erezer-store")
                .subject(userId.toString())
                .claim("email", userId + "@example.com")
                .claim(JwtTokenProvider.CLAIM_TOKEN_TYPE, JwtTokenProvider.TOKEN_TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return "Bearer " + token;
    }
}
