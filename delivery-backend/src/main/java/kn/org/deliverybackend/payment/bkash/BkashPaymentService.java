package kn.org.deliverybackend.payment.bkash;

/**
 * Abstraction over the bKash Tokenized Checkout API. Has two implementations:
 *
 * <ul>
 *   <li>{@code BkashSandboxStubClient} — in-memory simulator used in dev so
 *       the full storefront flow can be exercised without sandbox credentials.</li>
 *   <li>{@code BkashRestClient} — real HTTPS calls to {@code tokenized.sandbox.bka.sh}.
 *       Currently a documented stub; fill in once you have merchant creds.</li>
 * </ul>
 *
 * Switch implementations with {@code app.bkash.mode=STUB|REST}.
 */
public interface BkashPaymentService {

    /** Step 1: create a payment intent. Returns a payment id + redirect URL. */
    BkashPaymentResponse createPayment(BkashCreatePaymentRequest request);

    /** Step 2: after the customer authorises on bKash, execute (capture) the payment. */
    BkashPaymentResponse executePayment(String paymentId);

    /** Step 3: idempotent status query — used for reconciliation and the callback. */
    BkashPaymentResponse queryPayment(String paymentId);
}
