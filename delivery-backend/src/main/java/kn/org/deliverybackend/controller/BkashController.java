package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.Payment;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.payment.bkash.BkashCreatePaymentRequest;
import kn.org.deliverybackend.payment.bkash.BkashPaymentResponse;
import kn.org.deliverybackend.payment.bkash.BkashPaymentService;
import kn.org.deliverybackend.repository.OrderRepository;
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
 * Public bKash endpoints. The customer flow is:
 *
 *   POST /api/payments/bkash/init       — start a payment for an existing order
 *   GET  /api/payments/bkash/execute    — called by the storefront callback after redirect
 *   GET  /api/payments/bkash/query/{id} — idempotent status check
 *
 * Whitelisted in SecurityConfig because the callback is unauthenticated by
 * design (the customer is bouncing back from bKash's hosted page).
 */
@RestController
@RequestMapping("/api/payments/bkash")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments: bKash")
public class BkashController {

    private static final String PROVIDER = "BKASH";

    private final BkashPaymentService bkash;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @Value("${app.bkash.callback-url}")
    private String configuredCallbackUrl;

    @PostMapping("/init")
    public ResponseEntity<BkashPaymentResponse> init(@RequestBody InitRequest request) {
        Order order = orderRepository.findById(UUID.fromString(request.orderId))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.orderId));

        BkashCreatePaymentRequest bkashRequest = BkashCreatePaymentRequest.builder()
                .orderId(order.getId().toString())
                .amount(order.getTotalAmount())
                .currency("BDT")
                .intent("sale")
                .callbackUrl(configuredCallbackUrl)
                .customerEmail(order.getCustomerEmail())
                .build();

        BkashPaymentResponse response = bkash.createPayment(bkashRequest);

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(order.getId());
        payment.setMethod("BKASH");
        payment.setProvider(PROVIDER);
        payment.setProviderPaymentId(response.getPaymentId());
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(response.getStatus());
        payment.setCallbackUrl(configuredCallbackUrl);
        paymentRepository.save(payment);

        log.info("[bKash] Init for order {} → paymentId {}", order.getId(), response.getPaymentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Storefront calls this after the customer returns from the bKash page.
     * Idempotent: hitting it twice on the same paymentId just returns the
     * existing "Completed" state.
     */
    @PostMapping("/execute")
    public ResponseEntity<BkashPaymentResponse> execute(@RequestBody ExecuteRequest request) {
        BkashPaymentResponse response = bkash.executePayment(request.paymentId);
        paymentRepository.findByProviderAndProviderPaymentId(PROVIDER, request.paymentId)
                .ifPresent(payment -> {
                    payment.setStatus(response.getStatus());
                    payment.setProviderTrxId(response.getTrxId());
                    payment.setTransactionId(response.getTrxId());
                    paymentRepository.save(payment);
                });
        return ResponseEntity.ok(response);
    }

    @GetMapping("/query/{paymentId}")
    public ResponseEntity<BkashPaymentResponse> query(@PathVariable String paymentId) {
        return ResponseEntity.ok(bkash.queryPayment(paymentId));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InitRequest {
        @NotBlank
        private String orderId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecuteRequest {
        @NotBlank
        private String paymentId;
    }
}
