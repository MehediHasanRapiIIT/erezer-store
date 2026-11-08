package kn.org.deliverybackend.payment.bkash;

import com.sun.net.httpserver.HttpServer;
import kn.org.deliverybackend.exception.InvalidRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real bKash client against a pretend bKash, step by step as in "Tokenized Without
 * Agreement": token, Create Payment, Execute Payment, Query Payment when Execute gives
 * no answer, and bKash's own error messages passed on.
 */
class BkashRestClientTest {

    private HttpServer server;
    /** Each call: path, headers that matter, body. */
    private final List<String> calls = new ArrayList<>();
    private final Map<String, String> replies = new ConcurrentHashMap<>();
    private volatile String silentPath;

    private String start(String version) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath().replaceFirst("^/" + version, "");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.add(path + " auth=" + exchange.getRequestHeaders().getFirst("Authorization")
                    + " key=" + exchange.getRequestHeaders().getFirst("X-App-Key")
                    + " user=" + exchange.getRequestHeaders().getFirst("username") + " " + body);
            if (path.equals(silentPath)) {
                exchange.close(); // no answer at all
                return;
            }
            String reply = replies.getOrDefault(path, "{}");
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.contains("externalCode") ? 400 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + version;
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private static BkashCreatePaymentRequest order() {
        return BkashCreatePaymentRequest.builder().amount(new BigDecimal("1050")).currency("BDT").intent("sale")
                .callbackUrl("https://api.erezer.com/api/payments/bkash/callback")
                .payerReference("01712345678").invoiceNumber("ERZ-1001-ABC").build();
    }

    @Test
    void v2PaysAnOrderStepByStep() throws IOException {
        String base = start("v2");
        replies.put("/tokenized-checkout/auth/grant-token", "{\"id_token\":\"TOKEN1\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"refresh_token\":\"R1\"}");
        replies.put("/tokenized-checkout/payment/create", "{\"paymentId\":\"TR001\",\"bkashURL\":\"https://sandbox.payment.bkash.com/?paymentId=TR001\",\"transactionStatus\":\"Initiated\",\"amount\":\"1050.00\"}");
        replies.put("/tokenized-checkout/payment/execute", "{\"paymentId\":\"TR001\",\"trxId\":\"CDH00MS55A\",\"transactionStatus\":\"Completed\",\"amount\":\"1050.00\",\"payerAccount\":\"01712345678\"}");
        BkashRestClient bkash = new BkashRestClient(base, "KEY", "SECRET", "USER", "PASS");

        BkashPaymentResponse created = bkash.createPayment(order());
        assertEquals("TR001", created.getPaymentId());
        assertEquals("https://sandbox.payment.bkash.com/?paymentId=TR001", created.getBkashURL());
        assertTrue(calls.get(0).startsWith("/tokenized-checkout/auth/grant-token") && calls.get(0).contains("user=USER")
                && calls.get(0).contains("\"app_key\":\"KEY\""), "1. a token first, with the merchant's username and password");
        String create = calls.get(1);
        assertTrue(create.contains("auth=TOKEN1") && create.contains("key=KEY"), "the token and app key go with the call");
        assertTrue(create.contains("\"amount\":\"1050.00\"") && create.contains("\"intent\":\"sale\"")
                && create.contains("\"merchantInvoiceNumber\":\"ERZ-1001-ABC\"") && create.contains("\"payerReference\":\"01712345678\""));
        assertTrue(!create.contains("\"mode\""), "V2 sends no mode");

        BkashPaymentResponse paid = bkash.executePayment("TR001");
        assertEquals("Completed", paid.getStatus());
        assertEquals("CDH00MS55A", paid.getTrxId());
        assertEquals("01712345678", paid.getPayerAccount());
        assertTrue(calls.get(2).contains("{\"paymentId\":\"TR001\"}"));
        assertEquals(1, calls.stream().filter(c -> c.contains("grant-token")).count(), "the token is kept, not asked for each call");
    }

    @Test
    void whenExecuteGivesNoAnswerQueryPaymentDecides() throws IOException {
        String base = start("v2");
        replies.put("/tokenized-checkout/auth/grant-token", "{\"id_token\":\"T\",\"expires_in\":3600}");
        replies.put("/tokenized-checkout/query/payment", "{\"paymentId\":\"TR002\",\"trxId\":\"CDH1\",\"transactionStatus\":\"Completed\",\"amount\":\"500.00\"}");
        silentPath = "/tokenized-checkout/payment/execute";
        BkashRestClient bkash = new BkashRestClient(base, "KEY", "SECRET", "USER", "PASS");

        BkashPaymentResponse paid = bkash.executePayment("TR002");
        assertEquals("Completed", paid.getStatus());
        assertEquals("CDH1", paid.getTrxId());
        assertTrue(calls.stream().anyMatch(c -> c.startsWith("/tokenized-checkout/query/payment")));
    }

    @Test
    void anInitiatedPaymentAfterNoAnswerIsNotTakenAsPaid() throws IOException {
        String base = start("v2");
        replies.put("/tokenized-checkout/auth/grant-token", "{\"id_token\":\"T\",\"expires_in\":3600}");
        replies.put("/tokenized-checkout/query/payment", "{\"paymentId\":\"TR003\",\"transactionStatus\":\"Initiated\"}");
        silentPath = "/tokenized-checkout/payment/execute";
        BkashRestClient bkash = new BkashRestClient(base, "KEY", "SECRET", "USER", "PASS");

        BkashPaymentResponse result = bkash.executePayment("TR003");
        assertEquals("Initiated", result.getStatus(), "Initiated means the customer starts again with a new Create Payment");
        assertNull(result.getTrxId());
    }

    @Test
    void bkashsOwnMessageIsPassedOn() throws IOException {
        String base = start("v2");
        replies.put("/tokenized-checkout/auth/grant-token", "{\"id_token\":\"T\",\"expires_in\":3600}");
        replies.put("/tokenized-checkout/payment/execute", "{\"externalCode\":\"2023\",\"errorMessageEn\":\"Insufficient Balance\"}");
        replies.put("/tokenized-checkout/query/payment", "{\"paymentId\":\"TR004\",\"transactionStatus\":\"Initiated\"}");
        replies.put("/tokenized-checkout/payment/create", "{\"externalCode\":\"2006\",\"errorMessageEn\":\"Invalid amount\"}");
        BkashRestClient bkash = new BkashRestClient(base, "KEY", "SECRET", "USER", "PASS");

        BkashPaymentResponse failed = bkash.executePayment("TR004");
        assertEquals("Failed", failed.getStatus());
        assertEquals("Insufficient Balance", failed.getErrorMessage());
        InvalidRequestException refused = assertThrows(InvalidRequestException.class, () -> bkash.createPayment(order()));
        assertEquals("Invalid amount", refused.getMessage());
    }

    @Test
    void theBetaSandboxSpeaksItsOwnWords() throws IOException {
        String base = start("v1.2.0-beta");
        replies.put("/tokenized/checkout/token/grant", "{\"statusCode\":\"0000\",\"id_token\":\"BETA\",\"expires_in\":3600}");
        replies.put("/tokenized/checkout/create", "{\"statusCode\":\"0000\",\"statusMessage\":\"Successful\",\"paymentID\":\"TRB1\",\"bkashURL\":\"https://sandbox.payment.bkash.com/?paymentId=TRB1\",\"transactionStatus\":\"Initiated\"}");
        replies.put("/tokenized/checkout/execute", "{\"statusCode\":\"0000\",\"paymentID\":\"TRB1\",\"trxID\":\"BETA1\",\"transactionStatus\":\"Completed\",\"amount\":\"1050\",\"customerMsisdn\":\"01770618575\"}");
        BkashRestClient bkash = new BkashRestClient(base, "KEY", "SECRET", "USER", "PASS");

        assertEquals("TRB1", bkash.createPayment(order()).getPaymentId());
        assertTrue(calls.get(1).contains("\"mode\":\"0011\""), "the beta needs mode 0011 for checkout without agreement");
        BkashPaymentResponse paid = bkash.executePayment("TRB1");
        assertEquals("Completed", paid.getStatus());
        assertEquals("BETA1", paid.getTrxId());
        assertTrue(calls.get(2).contains("{\"paymentID\":\"TRB1\"}"));
    }

    @Test
    void v2RefundsPartOfAPayment() throws IOException {
        String base = start("v2");
        replies.put("/tokenized-checkout/auth/grant-token", "{\"id_token\":\"T\",\"expires_in\":3600}");
        replies.put("/tokenized-checkout/refund/payment/transaction", "{\"originalTrxId\":\"CDH1\",\"refundTrxId\":\"CDH00R1\",\"refundTransactionStatus\":\"Completed\",\"refundAmount\":\"100.00\"}");
        BkashRestClient bkash = new BkashRestClient(base, "KEY", "SECRET", "USER", "PASS");

        BkashRefundResponse back = bkash.refund("TR001", "CDH1", new BigDecimal("100"), "EZ-1001", "Wrong size");
        assertTrue(back.completed());
        assertEquals("CDH00R1", back.getRefundTrxId());
        assertEquals(0, new BigDecimal("100.00").compareTo(back.getAmount()));
        String call = calls.get(1);
        assertTrue(call.contains("\"paymentId\":\"TR001\"") && call.contains("\"refundAmount\":\"100.00\"")
                && call.contains("\"trxId\":\"CDH1\"") && call.contains("\"sku\":\"EZ-1001\"") && call.contains("\"reason\":\"Wrong size\""), call);
    }

    @Test
    void theBetaRefundsWithItsOwnWords() throws IOException {
        String base = start("v1.2.0-beta");
        replies.put("/tokenized/checkout/token/grant", "{\"id_token\":\"B\",\"expires_in\":3600}");
        replies.put("/tokenized/checkout/payment/refund", "{\"completedTime\":\"x\",\"transactionStatus\":\"Completed\",\"originalTrxID\":\"DK8\",\"refundTrxID\":\"DK9R\",\"amount\":\"50\"}");
        BkashRestClient bkash = new BkashRestClient(base, "KEY", "SECRET", "USER", "PASS");

        BkashRefundResponse back = bkash.refund("TRB1", "DK8", new BigDecimal("50"), "EZ-1", "Faulty");
        assertTrue(back.completed());
        assertEquals("DK9R", back.getRefundTrxId());
        assertTrue(calls.get(1).contains("\"paymentID\":\"TRB1\"") && calls.get(1).contains("\"trxID\":\"DK8\"")
                && calls.get(1).contains("\"amount\":\"50.00\""));
    }

    @Test
    void aRefusedRefundCarriesBkashsWords() throws IOException {
        String base = start("v2");
        replies.put("/tokenized-checkout/auth/grant-token", "{\"id_token\":\"T\",\"expires_in\":3600}");
        replies.put("/tokenized-checkout/refund/payment/transaction", "{\"externalCode\":\"2026\",\"errorMessageEn\":\"The reversal amount cannot be greater than the original transaction amount\"}");
        BkashRestClient bkash = new BkashRestClient(base, "KEY", "SECRET", "USER", "PASS");

        BkashRefundResponse back = bkash.refund("TR001", "CDH1", new BigDecimal("9999"), "EZ-1", "x");
        assertTrue(!back.completed());
        assertEquals("The reversal amount cannot be greater than the original transaction amount", back.getErrorMessage());
    }

    @Test
    void wrongCredentialsAreReportedPlainly() throws IOException {
        String base = start("v2");
        replies.put("/tokenized-checkout/auth/grant-token", "{\"externalCode\":\"9999\",\"errorMessageEn\":\"App key does not exist\"}");
        BkashRestClient bkash = new BkashRestClient(base, "KEY", "SECRET", "USER", "PASS");
        InvalidRequestException refused = assertThrows(InvalidRequestException.class, () -> bkash.createPayment(order()));
        assertTrue(refused.getMessage().contains("not available"));
    }
}
