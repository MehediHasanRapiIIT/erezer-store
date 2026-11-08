package kn.org.deliverybackend.payment.bkash;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bKash API log: every request and answer written, no secret ever in it. */
class BkashApiLogTest {

    private HttpServer server;
    private final ListAppender<ILoggingEvent> lines = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger("bkash.api");

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
        logger.detachAppender(lines);
    }

    @Test
    void aPaymentIsLoggedBothWaysWithSecretsHidden() throws IOException {
        Map<String, String> replies = Map.of(
                "/tokenized/checkout/token/grant", "{\"id_token\":\"SECRET-TOKEN-123\",\"refresh_token\":\"SECRET-REFRESH\",\"expires_in\":3600}",
                "/tokenized/checkout/create", "{\"paymentID\":\"TR9\",\"bkashURL\":\"https://sandbox.payment.bkash.com/?paymentId=TR9\",\"transactionStatus\":\"Initiated\"}");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath().replaceFirst("^/v1.2.0-beta", "");
            exchange.getRequestBody().readAllBytes();
            byte[] out = replies.getOrDefault(path, "{}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        lines.start();
        logger.addAppender(lines);

        BkashRestClient bkash = new BkashRestClient("http://127.0.0.1:" + server.getAddress().getPort() + "/v1.2.0-beta",
                "APPKEY12345", "MY-APP-SECRET", "merchantuser", "MY-PASSWORD");
        bkash.createPayment(BkashCreatePaymentRequest.builder().amount(new BigDecimal("460")).callbackUrl("https://x/cb")
                .payerReference("01770618575").invoiceNumber("EZ-1-A").build());

        List<String> log = lines.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertEquals(4, log.size(), "token request + answer, create request + answer: " + log);
        String all = String.join("\n", log);
        for (String secret : List.of("MY-APP-SECRET", "MY-PASSWORD", "merchantuser", "SECRET-TOKEN-123", "SECRET-REFRESH", "APPKEY12345", "01770618575")) {
            assertFalse(all.contains(secret), "never in the log: " + secret);
        }
        assertTrue(log.get(0).contains("→ POST") && log.get(0).contains("/tokenized/checkout/token/grant"));
        assertTrue(log.get(1).contains("← HTTP 200"));
        assertTrue(log.get(2).contains("/tokenized/checkout/create") && log.get(2).contains("\"amount\":\"460.00\"")
                && log.get(2).contains("\"merchantInvoiceNumber\":\"EZ-1-A\"") && log.get(2).contains("017*****575"));
        assertTrue(log.get(3).contains("\"paymentID\":\"TR9\""), "the answer is there in full: " + log.get(3));
    }
}
