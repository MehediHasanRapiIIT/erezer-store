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

    /**
     * Sends money back to the wallet that paid: all or part of a completed payment,
     * up to 10 times, within 60 days. Never throws for bKash's own refusals: the
     * answer says so, with bKash's message.
     */
    BkashRefundResponse refund(String paymentId, String trxId, java.math.BigDecimal amount, String sku, String reason);

    /** How many refunds bKash makes of one payment: 10 on V2, but only one on v1.2.0-beta. */
    default int maxRefunds() {
        return 10;
    }
}
