package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.Payment;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.payment.bkash.BkashCreatePaymentRequest;
import kn.org.deliverybackend.payment.bkash.BkashPaymentResponse;
import kn.org.deliverybackend.payment.bkash.BkashPaymentState;
import kn.org.deliverybackend.payment.bkash.BkashPaymentService;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.integration.meta.MetaConversionsService;
import kn.org.deliverybackend.integration.meta.RequestAttribution;
import kn.org.deliverybackend.repository.PaymentRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Public bKash endpoints, "without agreement" checkout. The customer flow is:
 *
 *   POST /api/payments/bkash/init       — start a payment for an order; answers bKash's page to go to
 *   GET  /api/payments/bkash/callback   — bKash sends the customer back here; the payment is
 *                                         executed here, then the customer goes on to the shop's
 *                                         /bkash-callback page
 *   POST /api/payments/bkash/execute    — that page asks how it went (executes it if the
 *                                         callback could not); safe to call again
 *   GET  /api/payments/bkash/query/{id} — the stored status
 *
 * Whitelisted in SecurityConfig because the callback is unauthenticated by
 * design (the customer is bouncing back from bKash's hosted page). Nothing a
 * customer sends decides that a payment succeeded: only bKash's own Execute or
 * Query Payment answer does.
 */
@RestController
@RequestMapping("/api/payments/bkash")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments: bKash")
public class BkashController {

    private static final String PROVIDER = "BKASH";

    private final BkashPaymentService bkash;
    private final kn.org.deliverybackend.service.RateLimiterService rateLimiter;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final MetaConversionsService metaConversions;
    private final kn.org.deliverybackend.service.StoreSettingsService storeSettingsService;

    @Value("${app.bkash.callback-url}")
    private String configuredCallbackUrl;

    @Value("${app.frontend.store-url}")
    private String storeUrl;

    @PostMapping("/init")
    public ResponseEntity<BkashPaymentResponse> init(@jakarta.validation.Valid @RequestBody InitRequest request,
                                                     jakarta.servlet.http.HttpServletRequest http) {
        rateLimiter.enforce("bkash:" + kn.org.deliverybackend.util.ClientIp.of(http), 20, java.time.Duration.ofMinutes(10));
        // Switched off in Settings: no new bKash payment, not even "Pay again" on an older order.
        if (Boolean.FALSE.equals(storeSettingsService.get().getPaymentBkashEnabled())) {
            throw new kn.org.deliverybackend.exception.InvalidRequestException(
                    "bKash payment isn't available right now. Please contact us to pay for this order.");
        }
        Order order;
        if (request.orderId != null && !request.orderId.isBlank()) {
            UUID orderId;
            try {
                orderId = UUID.fromString(request.orderId.trim());
            } catch (IllegalArgumentException ex) {
                throw new kn.org.deliverybackend.exception.InvalidRequestException("That order id isn't valid.");
            }
            order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.orderId));
        } else {
            // "Pay again" on the Track Order page, which knows only the order number.
            String number = kn.org.deliverybackend.util.OrderNumbers.normalize(request.orderNumber == null ? "" : request.orderNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
            order = orderRepository.findByOrderNumberAndDeletedFalse(number)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        }
        if ("PAID".equals(BkashPaymentState.of(order.getPaymentMethod(), paymentRepository.findByOrderId(order.getId())))) {
            throw new kn.org.deliverybackend.exception.InvalidRequestException("This order is already paid.");
        }
        // Only a bKash order that is still waiting for its money can start a payment.
        if (!"BKASH".equalsIgnoreCase(order.getPaymentMethod())) {
            throw new kn.org.deliverybackend.exception.InvalidRequestException("This order isn't paid with bKash.");
        }
        if (!"PLACED".equalsIgnoreCase(order.getOrderStatus())) {
            throw new kn.org.deliverybackend.exception.InvalidRequestException("This order can no longer be paid.");
        }

        BkashCreatePaymentRequest bkashRequest = BkashCreatePaymentRequest.builder()
                .orderId(order.getId().toString())
                .amount(order.getTotalAmount())
                .currency("BDT")
                .intent("sale")
                .callbackUrl(configuredCallbackUrl)
                .customerEmail(order.getCustomerEmail())
                .payerReference(payerReference(order))
                .invoiceNumber(invoiceNumber(order))
                .build();

        BkashPaymentResponse response = bkash.createPayment(bkashRequest);

        // No id here: the entity generates one on insert. A hand-assigned id
        // makes Spring Data treat the new row as an update and the save fails.
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setMethod("BKASH");
        payment.setProvider(PROVIDER);
        payment.setProviderPaymentId(response.getPaymentId());
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(response.getStatus());
        payment.setCallbackUrl(configuredCallbackUrl);
        paymentRepository.save(payment);

        log.info("[bKash] Init for order {} → paymentId {}", order.getId(), response.getPaymentId());
        response.setOrderId(order.getId().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Where bKash sends the customer back, with ?paymentID=…&status=success|failure|cancel.
     * A success is executed here at once, so the money is recorded even if the customer
     * closes the tab; then the customer goes on to the shop's page, which shows the result.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(name = "paymentID", required = false) String paymentID,
                                         @RequestParam(name = "paymentId", required = false) String paymentIdAlt,
                                         @RequestParam(name = "status", required = false) String status) {
        String paymentId = paymentID != null ? paymentID : paymentIdAlt;
        String outcome = status == null ? "failure" : status.trim().toLowerCase();
        org.slf4j.LoggerFactory.getLogger("bkash.api").info("[bKash API] callback from bKash: paymentID={} status={}", paymentId, status);
        String orderId = "";
        if (paymentId != null && !paymentId.isBlank() && paymentId.length() <= 128) {
            var payment = paymentRepository.findByProviderAndProviderPaymentId(PROVIDER, paymentId);
            if (payment.isPresent()) {
                orderId = payment.get().getOrderId() == null ? "" : payment.get().getOrderId().toString();
                if ("success".equals(outcome)) {
                    BkashPaymentResponse result = settle(paymentId);
                    if (!"Completed".equalsIgnoreCase(result.getStatus())) outcome = "failure";
                } else {
                    markUnpaid(payment.get(), "cancel".equals(outcome) ? "Cancelled" : "Failed");
                }
            } else {
                outcome = "failure";
            }
        }
        String target = storeUrl + "/bkash-callback?paymentId=" + enc(paymentId == null ? "" : paymentId)
                + "&status=" + enc(outcome) + "&orderId=" + enc(orderId);
        return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create(target)).build();
    }

    /**
     * Storefront calls this after the customer returns from the bKash page.
     * Idempotent: hitting it twice on the same paymentId just returns the
     * existing "Completed" state.
     */
    @PostMapping("/execute")
    public ResponseEntity<BkashPaymentResponse> execute(@jakarta.validation.Valid @RequestBody ExecuteRequest request,
                                                        jakarta.servlet.http.HttpServletRequest http) {
        rateLimiter.enforce("bkash:" + kn.org.deliverybackend.util.ClientIp.of(http), 20, java.time.Duration.ofMinutes(10));
        return ResponseEntity.ok(settle(request.paymentId));
    }

    /** The stored status of a payment. */
    @GetMapping("/query/{paymentId}")
    public ResponseEntity<BkashPaymentResponse> query(@PathVariable String paymentId,
                                                      jakarta.servlet.http.HttpServletRequest http) {
        rateLimiter.enforce("bkash:" + kn.org.deliverybackend.util.ClientIp.of(http), 20, java.time.Duration.ofMinutes(10));
        Payment payment = paymentRepository.findByProviderAndProviderPaymentId(PROVIDER, paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found."));
        return ResponseEntity.ok(stored(payment));
    }

    /**
     * Executes a payment once, and records what bKash says. A payment already completed
     * answers from what was recorded, so the callback and the shop's page can both ask.
     */
    private synchronized BkashPaymentResponse settle(String paymentId) {
        Payment payment = paymentRepository.findByProviderAndProviderPaymentId(PROVIDER, paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found."));
        if ("Completed".equalsIgnoreCase(payment.getStatus())) return stored(payment);

        BkashPaymentResponse response = bkash.executePayment(paymentId);
        boolean completed = "Completed".equalsIgnoreCase(response.getStatus());
        if (completed && response.getAmount() != null && payment.getAmount() != null
                && response.getAmount().compareTo(payment.getAmount()) != 0) {
            log.error("[bKash] Payment {} completed for {} but the order asked {}", paymentId, response.getAmount(), payment.getAmount());
        }
        payment.setStatus(completed ? "Completed" : response.getStatus() == null ? "Failed" : response.getStatus());
        if (response.getTrxId() != null) {
            payment.setProviderTrxId(response.getTrxId());
            payment.setTransactionId(response.getTrxId());
        }
        if (response.getPayerAccount() != null) payment.setPayerAccount(response.getPayerAccount());
        paymentRepository.save(payment);
        log.info("[bKash] Execute {} → {} {}", paymentId, payment.getStatus(), response.getTrxId() == null ? "" : response.getTrxId());

        // The money has actually moved now, so this is the moment a bKash order
        // becomes a sale for Meta. Only on the first completion, never twice.
        if (completed && payment.getOrderId() != null) {
            metaConversions.sendPurchase(payment.getOrderId(), RequestAttribution.capture());
        }
        response.setOrderId(payment.getOrderId() == null ? null : payment.getOrderId().toString());
        return response;
    }

    /** The customer cancelled on bKash's page, or it failed there: nothing was taken. */
    private void markUnpaid(Payment payment, String status) {
        if ("Completed".equalsIgnoreCase(payment.getStatus())) return;
        payment.setStatus(status);
        paymentRepository.save(payment);
        log.info("[bKash] Payment {} {}", payment.getProviderPaymentId(), status.toLowerCase());
    }

    private static BkashPaymentResponse stored(Payment payment) {
        return BkashPaymentResponse.builder()
                .status(payment.getStatus())
                .paymentId(payment.getProviderPaymentId())
                .trxId(payment.getProviderTrxId())
                .amount(payment.getAmount())
                .currency("BDT")
                .payerAccount(payment.getPayerAccount())
                .orderId(payment.getOrderId() == null ? null : payment.getOrderId().toString())
                .build();
    }

    /** The customer's mobile number fills the wallet box on bKash's page; otherwise the order number. */
    static String payerReference(Order order) {
        String digits = order.getCustomerPhone() == null ? "" : order.getCustomerPhone().replaceAll("\\D", "");
        if (digits.startsWith("880")) digits = digits.substring(2);
        if (digits.matches("01\\d{9}")) return digits;
        return order.getOrderNumber() != null ? order.getOrderNumber() : order.getId().toString().substring(0, 8);
    }

    /** Unique for each try: bKash refuses an invoice number it has seen, and an order may be paid again. */
    static String invoiceNumber(Order order) {
        String base = order.getOrderNumber() != null ? order.getOrderNumber() : order.getId().toString().substring(0, 8);
        return base + "-" + Long.toString(System.currentTimeMillis() / 1000, 36).toUpperCase();
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InitRequest {
        /** The order's id, or its number (from the Track Order page). One of the two. */
        @jakarta.validation.constraints.Size(max = 64)
        private String orderId;
        @jakarta.validation.constraints.Size(max = 40)
        private String orderNumber;

        public InitRequest(String orderId) {
            this.orderId = orderId;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecuteRequest {
        @NotBlank
        @jakarta.validation.constraints.Size(max = 128)
        private String paymentId;
    }
}
