package kn.org.deliverybackend.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockUpdateEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockUpdate(StockUpdateEvent event) {
        StockUpdateMessage message = new StockUpdateMessage(
                event.getProductId(),
                event.getStockQuantity(),
                event.getStockStatus(),
                Instant.now()
        );
        String destination = "/topic/stock/" + event.getProductId();
        log.debug("Publishing stock update to {}: qty={}, status={}",
                destination, event.getStockQuantity(), event.getStockStatus());
        messagingTemplate.convertAndSend(destination, message);
    }
}
