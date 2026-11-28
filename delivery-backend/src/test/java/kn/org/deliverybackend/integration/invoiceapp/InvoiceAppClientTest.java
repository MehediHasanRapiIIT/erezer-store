package kn.org.deliverybackend.integration.invoiceapp;

import com.sun.net.httpserver.HttpServer;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppClient.KeyRefusedException;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppClient.OrderRefusedException;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppClient.Reply;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppClient.UnreachableException;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.Outcome;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.ShopCustomer;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.ShopItem;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.ShopOrder;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppSettingsService.Connection;
import kn.org.deliverybackend.service.FileStorageService.StoredFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The requests Erezer Invoice gets, and how its answers are read. A small local server plays its part. */
class InvoiceAppClientTest {

    private HttpServer server;
    private Connection to;
    private final InvoiceAppClient client = new InvoiceAppClient();

    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> key = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private volatile int status = 201;
    private volatile String answer = "{\"orderNumber\":\"EZ-1\",\"outcome\":\"CREATED\",\"orderId\":152,\"missingPhotos\":0}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/integrations/shop/", exchange -> {
            key.set(exchange.getRequestHeaders().getFirst("X-Shop-Key"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] out = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        to = new Connection("http://127.0.0.1:" + server.getAddress().getPort(), "ezs_test-key-0123456789abcdef");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void anOrderGoesAsJsonWithItsPhotosBesideIt() {
        Reply reply = client.send(to, prepared());

        assertEquals(Outcome.SENT, reply.outcome());
        assertEquals(152L, reply.invoiceOrderId());
        assertEquals("ezs_test-key-0123456789abcdef", key.get());
        assertTrue(contentType.get().startsWith("multipart/form-data"));
        String sent = body.get();
        assertTrue(sent.contains("name=\"order\""), sent);
        assertTrue(sent.contains("\"orderNumber\":\"EZ-1\""), "the order as JSON");
        assertTrue(sent.contains("\"orderedAt\":\"2026-11-20T08:30:00Z\""), "dates as text, not numbers");
        assertTrue(sent.contains("name=\"item-0\"; filename=\"black.jpg\""), "the first line's photo");
        assertTrue(!sent.contains("name=\"item-1\""), "the second line has no photo");
    }

    @Test
    void alreadyThereIsReadAsSuch() {
        status = 200;
        answer = "{\"orderNumber\":\"EZ-1\",\"outcome\":\"EXISTS\",\"orderId\":152,\"missingPhotos\":0}";
        assertEquals(Outcome.ALREADY_THERE, client.send(to, prepared()).outcome());
    }

    @Test
    void aWrongKeyStopsEverything() {
        status = 401;
        answer = "{\"code\":\"SHOP_KEY_INVALID\",\"detail\":\"The shop key is missing or wrong.\"}";
        assertThrows(KeyRefusedException.class, () -> client.send(to, prepared()));
        assertThrows(KeyRefusedException.class, () -> client.ping(to));
    }

    @Test
    void aRefusedOrderSaysWhichFieldsWereWrong() {
        status = 400;
        answer = "{\"detail\":\"Some fields are invalid\",\"code\":\"VALIDATION_FAILED\","
                + "\"fieldErrors\":{\"customer.phone\":\"is not a valid phone number\"}}";
        OrderRefusedException e = assertThrows(OrderRefusedException.class, () -> client.send(to, prepared()));
        assertEquals("Erezer Invoice refused it: Some fields are invalid: customer.phone is not a valid phone number", e.getMessage());
    }

    @Test
    void nothingListeningIsUnreachable() {
        server.stop(0);
        assertThrows(UnreachableException.class, () -> client.ping(to));
    }

    @Test
    void thePingCarriesTheKey() {
        status = 200;
        answer = "{\"ok\":true}";
        client.ping(to);
        assertEquals("ezs_test-key-0123456789abcdef", key.get());
    }

    private static InvoiceOrderBuilder.Prepared prepared() {
        ShopOrder order = new ShopOrder("EZ-1", Instant.parse("2026-11-20T08:30:00Z"), "CASH", BigDecimal.ZERO,
                new ShopCustomer("Rahim", "+8801712345678", null, "Dhanmondi"), new BigDecimal("60.00"), BigDecimal.ZERO,
                List.of(new ShopItem("Tee", null, null, "Black", "L", new BigDecimal("500.00"), 1, "Black · L"),
                        new ShopItem("Custom size charge: Tee", null, null, null, null, new BigDecimal("150.00"), 1, null)));
        return new InvoiceOrderBuilder.Prepared(order,
                List.of(List.of(new StoredFile("black.jpg", "image/jpeg", new byte[] {1, 2, 3})), List.of()), 0);
    }
}
