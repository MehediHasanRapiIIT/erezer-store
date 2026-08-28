package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.OrderItem;
import kn.org.deliverybackend.event.StockUpdateEvent;
import kn.org.deliverybackend.repository.OrderItemRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import kn.org.deliverybackend.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Returns reserved units to stock when an order is cancelled.
 *
 * <p>Placing an order decrements inventory up front (see
 * {@code OrderServiceImpl#computeAndReserveStock}), so cancelling has to credit
 * those units back or the stock figure drifts down permanently and products
 * eventually read as out-of-stock while the shelf is full.
 *
 * <p>Shared by both cancellation routes — the customer's self-service cancel and
 * an admin moving an order to CANCELLED — because either one leaves the same
 * hole in inventory.
 *
 * <p><b>Idempotency</b> is the callers' responsibility and comes for free from
 * the status machine: both paths reject a transition into CANCELLED from an
 * order that is already CANCELLED, so this runs at most once per order.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderStockRestorer {

    private final OrderItemRepository orderItemRepository;
    private final VariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void restoreForCancelledOrder(UUID orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            if (qty <= 0) {
                continue;
            }

            // Made-to-order lines never reserved stock, so they must not return
            // any - crediting them would invent inventory that never existed.
            if (item.getCustomMeasurements() != null && !item.getCustomMeasurements().isBlank()) {
                continue;
            }

            if (item.getVariantId() != null) {
                // Variant lines were decremented on the variant row, not the
                // product's inventory, so they have to be credited back there.
                variantRepository.findById(item.getVariantId()).ifPresentOrElse(variant -> {
                    int current = variant.getStockQuantity() != null ? variant.getStockQuantity() : 0;
                    variant.setStockQuantity(current + qty);
                    variantRepository.save(variant);
                    log.info("Order {} cancelled: returned {} unit(s) to variant {}", orderId, qty, variant.getId());
                }, () -> log.warn("Order {} cancelled: variant {} no longer exists, {} unit(s) not restored",
                        orderId, item.getVariantId(), qty));
                continue;
            }

            if (item.getProductId() == null) {
                continue;
            }
            productRepository.findById(item.getProductId()).ifPresentOrElse(product -> {
                inventoryService.incrementStock(product, qty);
                // Keep the live stock badges on the storefront in step, the same
                // way the decrement on order placement does.
                eventPublisher.publishEvent(new StockUpdateEvent(
                        this,
                        product.getId(),
                        inventoryService.getAvailableStock(product.getId()),
                        inventoryService.computeStatus(product)));
                log.info("Order {} cancelled: returned {} unit(s) to product {}", orderId, qty, product.getId());
            }, () -> log.warn("Order {} cancelled: product {} no longer exists, {} unit(s) not restored",
                    orderId, item.getProductId(), qty));
        }
    }
}
