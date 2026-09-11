package kn.org.deliverybackend.integration.meta;

import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.OrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Conversions API payload is what Meta attributes ad spend against, and
 * the hashed identifiers are what let it match a purchase to a person, so both
 * are pinned exactly.
 */
class MetaEventBuilderTest {

    private static final UUID ORDER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    // ── normalisation ───────────────────────────────────────────────────────

    @Test
    void emailIsLowercasedAndTrimmed() {
        assertEquals("rahim@example.com", MetaEventBuilder.normaliseEmail("  Rahim@Example.COM "));
        assertNull(MetaEventBuilder.normaliseEmail("   "));
        assertNull(MetaEventBuilder.normaliseEmail(null));
    }

    @Test
    void bangladeshiPhonesGetTheCountryCode() {
        assertEquals("8801712345678", MetaEventBuilder.normalisePhone("01712-345678"));
        assertEquals("8801712345678", MetaEventBuilder.normalisePhone("+880 1712 345678"));
        assertEquals("8801712345678", MetaEventBuilder.normalisePhone("8801712345678"));
        assertNull(MetaEventBuilder.normalisePhone("abc"));
    }

    @Test
    void sha256MatchesTheKnownDigest() {
        // Published test vector, so the hashing agrees with Meta's expectation.
        assertEquals("973dfe463ec85785f5f95af5ba3906eedb2d931c24e69824a89ea65dba4e813b",
                MetaEventBuilder.sha256("test@example.com"));
    }

    @Test
    void namesSplitIntoFirstAndLast() {
        assertEquals("rahim", MetaEventBuilder.splitName("Rahim Ahmed")[0]);
        assertEquals("ahmed", MetaEventBuilder.splitName("Rahim Ahmed")[1]);
        assertEquals("fatema", MetaEventBuilder.splitName("Fatema")[0]);
        assertNull(MetaEventBuilder.splitName("Fatema")[1]);
    }

    // ── the purchase event ──────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void purchaseEventCarriesHashedIdentityAndEveryLine() {
        Order order = order();
        List<OrderItem> items = List.of(item(31L, 2, "1400.00"), item(22L, 1, "3100.00"));
        RequestAttribution attribution = new RequestAttribution(
                "103.4.5.6", "Mozilla/5.0", "fb.1.1700000000.123", "fb.1.1700000000.AbC");

        Map<String, Object> event = MetaEventBuilder.purchase(
                order, items, Map.of(31L, "Scarlet Flare Dress"), attribution,
                "https://erezer.com/", Instant.ofEpochSecond(1_757_000_000L));

        assertEquals("Purchase", event.get("event_name"));
        assertEquals(1_757_000_000L, event.get("event_time"));
        assertEquals("purchase-" + ORDER_ID, event.get("event_id"));
        assertEquals("https://erezer.com/orders/" + ORDER_ID, event.get("event_source_url"));
        assertEquals("website", event.get("action_source"));

        Map<String, Object> user = (Map<String, Object>) event.get("user_data");
        assertEquals(List.of(MetaEventBuilder.sha256("rahim@example.com")), user.get("em"));
        assertEquals(List.of(MetaEventBuilder.sha256("8801712345678")), user.get("ph"));
        assertEquals(List.of(MetaEventBuilder.sha256("rahim")), user.get("fn"));
        assertEquals(List.of(MetaEventBuilder.sha256("ahmed")), user.get("ln"));
        assertEquals(List.of(MetaEventBuilder.sha256(CLIENT_ID.toString())), user.get("external_id"));
        assertEquals(List.of(MetaEventBuilder.sha256("bd")), user.get("country"));
        assertEquals("103.4.5.6", user.get("client_ip_address"));
        assertEquals("Mozilla/5.0", user.get("client_user_agent"));
        assertEquals("fb.1.1700000000.123", user.get("fbp"));
        assertEquals("fb.1.1700000000.AbC", user.get("fbc"));
        // Nothing personal leaves in clear text.
        assertFalse(user.containsValue("rahim@example.com"));
        assertFalse(user.containsValue("01712-345678"));

        Map<String, Object> data = (Map<String, Object>) event.get("custom_data");
        assertEquals("BDT", data.get("currency"));
        assertEquals(6060.0, data.get("value"));
        assertEquals(List.of("31", "22"), data.get("content_ids"));
        assertEquals(3, data.get("num_items"));
        assertEquals(ORDER_ID.toString(), data.get("order_id"));
        List<Map<String, Object>> contents = (List<Map<String, Object>>) data.get("contents");
        assertEquals(2, contents.size());
        assertEquals("31", contents.get(0).get("id"));
        assertEquals(2, contents.get(0).get("quantity"));
        assertEquals(1400.0, contents.get(0).get("item_price"));
        assertEquals("Scarlet Flare Dress", contents.get(0).get("title"));
        assertFalse(contents.get(1).containsKey("title"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void guestOrderWithoutIdentityStillReportsTheSale() {
        Order order = order();
        order.setClientId(null);
        order.setCustomerEmail(null);
        order.setCustomerPhone(null);
        order.setCustomerName(null);

        Map<String, Object> event = MetaEventBuilder.purchase(
                order, List.of(item(31L, 1, "1400.00")), Map.of(), RequestAttribution.NONE,
                "https://erezer.com", Instant.EPOCH);

        Map<String, Object> user = (Map<String, Object>) event.get("user_data");
        assertFalse(user.containsKey("em"));
        assertFalse(user.containsKey("ph"));
        assertFalse(user.containsKey("external_id"));
        assertFalse(user.containsKey("client_ip_address"));
        assertTrue(user.containsKey("country"));
        assertEquals("purchase-" + ORDER_ID, event.get("event_id"));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static Order order() {
        Order o = new Order();
        o.setId(ORDER_ID);
        o.setClientId(CLIENT_ID);
        o.setCustomerEmail(" Rahim@Example.com ");
        o.setCustomerPhone("01712-345678");
        o.setCustomerName("Rahim Ahmed");
        o.setPaymentMethod("CASH");
        o.setTotalAmount(new BigDecimal("6060.00"));
        return o;
    }

    private static OrderItem item(long productId, int qty, String price) {
        OrderItem i = new OrderItem();
        i.setId(UUID.randomUUID());
        i.setOrderId(ORDER_ID);
        i.setProductId(productId);
        i.setQuantity(qty);
        i.setPriceAtOrder(new BigDecimal(price));
        return i;
    }
}
