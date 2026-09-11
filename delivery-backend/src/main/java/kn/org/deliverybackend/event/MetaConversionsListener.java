package kn.org.deliverybackend.event;

import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.integration.meta.MetaConversionsService;
import kn.org.deliverybackend.integration.meta.RequestAttribution;
import kn.org.deliverybackend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reports a new order to Meta as a Purchase, for payment methods where the
 * order itself is the commitment (cash on delivery, card).
 *
 * <p>bKash orders are deliberately skipped here: the shopper has not paid yet
 * when the order row is created. Those are reported from the bKash execute
 * step once the payment is confirmed, so an abandoned bKash screen never
 * counts as a sale.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MetaConversionsListener {

    private final MetaConversionsService meta;
    private final OrderRepository orderRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        if (!meta.enabled()) return;
        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) return;
        if ("BKASH".equalsIgnoreCase(order.getPaymentMethod())) return;

        // Still on the request thread here, so the shopper's IP and pixel
        // cookies can be read before the send moves to the async executor.
        meta.sendPurchase(order.getId(), RequestAttribution.capture());
    }
}
