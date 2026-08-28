package kn.org.deliverybackend.payment.bkash;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory bKash simulator. Activated when {@code app.bkash.mode=STUB} (default).
 *
 * Flow:
 *   1. {@link #createPayment} stores the request, hands back a redirect URL pointing
 *      at the storefront callback with the synthetic payment id.
 *   2. The storefront's callback page POSTs back to /api/payments/bkash/execute
 *      which calls {@link #executePayment}: we move state Initiated → Completed.
 *   3. {@link #queryPayment} returns the latest state.
 *
 * Replace by setting {@code app.bkash.mode=REST} once {@code BkashRestClient} is wired.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "app.bkash.mode", havingValue = "STUB", matchIfMissing = true)
public class BkashSandboxStubClient implements BkashPaymentService {

    private final ConcurrentHashMap<String, BkashPaymentResponse> store = new ConcurrentHashMap<>();

    @Value("${app.frontend.store-url}")
    private String storeUrl;

    @Override
    public BkashPaymentResponse createPayment(BkashCreatePaymentRequest request) {
        String paymentId = "STUB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        // The "bKash URL" in the stub points at the storefront callback so the
        // browser round-trip happens locally — no hosted page to redirect to.
        String redirectUrl = storeUrl + "/bkash-callback?paymentId=" + paymentId
                + "&status=success&orderId=" + (request.getOrderId() == null ? "" : request.getOrderId());

        BkashPaymentResponse response = BkashPaymentResponse.builder()
                .status("Initiated")
                .paymentId(paymentId)
                .bkashURL(redirectUrl)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .merchantInvoiceNumber(request.getOrderId())
                .build();
        store.put(paymentId, response);
        log.info("[bKash-stub] Created payment {} for order {}", paymentId, request.getOrderId());
        return response;
    }

    @Override
    public BkashPaymentResponse executePayment(String paymentId) {
        BkashPaymentResponse current = store.get(paymentId);
        if (current == null) {
            return BkashPaymentResponse.builder()
                    .status("Failed")
                    .paymentId(paymentId)
                    .errorMessage("Unknown payment id (stub).")
                    .build();
        }
        if ("Completed".equals(current.getStatus())) {
            return current;
        }
        BkashPaymentResponse completed = BkashPaymentResponse.builder()
                .status("Completed")
                .paymentId(paymentId)
                .bkashURL(current.getBkashURL())
                .amount(current.getAmount())
                .currency(current.getCurrency())
                .merchantInvoiceNumber(current.getMerchantInvoiceNumber())
                .trxId("TXN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase())
                .build();
        store.put(paymentId, completed);
        log.info("[bKash-stub] Executed payment {} (trx {})", paymentId, completed.getTrxId());
        return completed;
    }

    @Override
    public BkashPaymentResponse queryPayment(String paymentId) {
        BkashPaymentResponse hit = store.get(paymentId);
        if (hit != null) return hit;
        return BkashPaymentResponse.builder()
                .status("Failed")
                .paymentId(paymentId)
                .errorMessage("Unknown payment id (stub).")
                .build();
    }
}
