package kn.org.deliverybackend.payment.bkash;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real bKash Tokenized Checkout client — activated when {@code app.bkash.mode=REST}.
 *
 * <p><b>NOT YET IMPLEMENTED.</b> The intentionally-stubbed methods document the
 * call shape so the wiring can be completed when sandbox credentials arrive.
 * Reference: <a href="https://developer.bka.sh">developer.bka.sh</a>.
 *
 * <p>Steps to finish:
 * <ol>
 *   <li>POST {@code /tokenized/checkout/token/grant} with {@code app_key}, {@code app_secret},
 *       {@code username}, {@code password} → cache the {@code id_token} (1h TTL).</li>
 *   <li>POST {@code /tokenized/checkout/create} with the token, merchantInvoiceNumber,
 *       amount, callbackURL → returns {@code paymentID} + {@code bkashURL}.</li>
 *   <li>After the customer redirects back, POST {@code /tokenized/checkout/execute/{paymentID}}.</li>
 *   <li>For reconciliation: GET {@code /tokenized/checkout/payment/status/{paymentID}}.</li>
 * </ol>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.bkash.mode", havingValue = "REST")
public class BkashRestClient implements BkashPaymentService {

    @Value("${app.bkash.base-url}")    private String baseUrl;
    @Value("${app.bkash.app-key}")     private String appKey;
    @Value("${app.bkash.app-secret}")  private String appSecret;
    @Value("${app.bkash.username}")    private String username;
    @Value("${app.bkash.password}")    private String password;
    @Value("${app.bkash.callback-url}")private String callbackUrl;

    @Override
    public BkashPaymentResponse createPayment(BkashCreatePaymentRequest request) {
        log.warn("BkashRestClient.createPayment called but not yet implemented — wire up sandbox calls here.");
        throw new UnsupportedOperationException(
                "bKash REST client is not yet implemented. Set app.bkash.mode=STUB or finish wiring "
                + "BkashRestClient against tokenized.sandbox.bka.sh.");
    }

    @Override
    public BkashPaymentResponse executePayment(String paymentId) {
        throw new UnsupportedOperationException("BkashRestClient.executePayment not yet implemented.");
    }

    @Override
    public BkashPaymentResponse queryPayment(String paymentId) {
        throw new UnsupportedOperationException("BkashRestClient.queryPayment not yet implemented.");
    }
}
