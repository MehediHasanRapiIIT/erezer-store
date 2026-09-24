package kn.org.deliverybackend.integration.meta;

import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.OrderItem;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.repository.OrderItemRepository;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends purchases to Meta's Conversions API, server to server.
 *
 * <p>The browser pixel alone misses every shopper with an ad blocker, most
 * iPhones, and much of the Facebook in-app browser, which is where a lot of
 * Bangladeshi traffic comes from. This sends the same Purchase from the
 * backend with the same event id, so Meta counts it once but reliably.
 *
 * <p>Off unless both {@code app.meta.pixel-id} and
 * {@code app.meta.capi-access-token} are set. Failures are logged and never
 * affect the order: an analytics outage must not stop a sale.
 */
@Service
@Slf4j
public class MetaConversionsService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final RestClient restClient;

    private final kn.org.deliverybackend.service.MetaPixelSettingsService metaSettings;
    private final String baseUrl;
    private final String apiVersion;
    private final String storeUrl;

    public MetaConversionsService(OrderRepository orderRepository,
                                  OrderItemRepository orderItemRepository,
                                  ProductRepository productRepository,
                                  kn.org.deliverybackend.service.MetaPixelSettingsService metaSettings,
                                  @Value("${app.meta.capi-base-url:https://graph.facebook.com}") String baseUrl,
                                  @Value("${app.meta.api-version:v23.0}") String apiVersion,
                                  @Value("${app.frontend.store-url}") String storeUrl) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.metaSettings = metaSettings;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiVersion = apiVersion;
        this.storeUrl = storeUrl;
        this.restClient = RestClient.create();
    }

    /**
     * True when the shop can report sales from the server: a Pixel ID and an
     * access token, set in the admin panel or in the server's configuration.
     */
    public boolean enabled() {
        return metaSettings.credentials().serverReportingOn();
    }

    /**
     * Report a paid order. Runs off the request thread; capture
     * {@link RequestAttribution} before calling so the shopper's IP and cookies
     * are still available.
     */
    @Async("metaExecutor")
    public void sendPurchase(UUID orderId, RequestAttribution attribution) {
        if (!enabled()) return;
        try {
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null) {
                log.warn("Meta CAPI: order {} not found, nothing sent", orderId);
                return;
            }
            List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
            Map<Long, String> names = new HashMap<>();
            for (Product p : productRepository.findAllById(
                    items.stream().map(OrderItem::getProductId).filter(id -> id != null).toList())) {
                names.put(p.getId(), p.getName());
            }
            Map<String, Object> event = MetaEventBuilder.purchase(
                    order, items, names, attribution, storeUrl, Instant.now());
            post(event);
        } catch (Exception ex) {
            log.warn("Meta CAPI: failed to send Purchase for order {}: {}", orderId, ex.getMessage());
        }
    }

    private void post(Map<String, Object> event) {
        // Read at send time, so a change in the admin panel applies to the next sale.
        var live = metaSettings.credentials();
        if (!live.serverReportingOn()) return;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", List.of(event));
        if (live.testEventCode() != null) body.put("test_event_code", live.testEventCode());

        String url = baseUrl + "/" + apiVersion + "/" + live.pixelId() + "/events";
        String response = restClient.post()
                .uri(url + "?access_token={token}", live.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        log.info("Meta CAPI: sent {} ({}) → {}", event.get("event_name"), event.get("event_id"), response);
    }
}
