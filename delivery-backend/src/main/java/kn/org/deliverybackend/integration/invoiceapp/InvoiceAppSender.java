package kn.org.deliverybackend.integration.invoiceapp;

import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.OrderInvoiceSend;
import kn.org.deliverybackend.enumeration.OrderStatus;
import kn.org.deliverybackend.exception.ExternalServiceException;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppClient.KeyRefusedException;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppClient.OrderRefusedException;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppClient.Reply;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppClient.UnreachableException;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.Outcome;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.SendResult;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.SentMark;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppSettingsService.Connection;
import kn.org.deliverybackend.repository.OrderInvoiceSendRepository;
import kn.org.deliverybackend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends the orders staff chose to Erezer Invoice, one after another, and
 * remembers which ones arrived. Sending one again is harmless: Erezer Invoice
 * answers "already there" and makes no second copy.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InvoiceAppSender {

    public static final int MAX_PER_SEND = 50;

    private final InvoiceAppSettingsService settings;
    private final InvoiceOrderBuilder builder;
    private final InvoiceAppClient client;
    private final OrderRepository orderRepository;
    private final OrderInvoiceSendRepository sentRepository;

    /** Checks the saved address and key with Erezer Invoice. */
    public void test() {
        try {
            client.ping(connection());
        } catch (KeyRefusedException | UnreachableException e) {
            throw new ExternalServiceException(e.getMessage());
        }
    }

    /** One result per order, in the order they were chosen. */
    public List<SendResult> send(List<UUID> orderIds, String sentBy) {
        Connection to = connection();
        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(orderIds));
        if (ids.size() > MAX_PER_SEND) {
            throw new InvalidRequestException("Send at most " + MAX_PER_SEND + " orders at a time.");
        }

        List<SendResult> results = new ArrayList<>();
        String stopped = null;   // set when nothing more can go: a refused key, Erezer Invoice down
        for (UUID id : ids) {
            Order order = orderRepository.findById(id).filter(o -> !Boolean.TRUE.equals(o.getDeleted())).orElse(null);
            String number = order == null ? null : order.getOrderNumber();
            if (order == null) {
                results.add(failed(id, null, "The order wasn't found."));
                continue;
            }
            if (stopped != null) {
                results.add(failed(id, number, stopped));
                continue;
            }
            if (OrderStatus.CANCELLED.name().equalsIgnoreCase(order.getOrderStatus())) {
                results.add(failed(id, number, "Cancelled orders can't be sent."));
                continue;
            }
            if (number == null || number.isBlank()) {
                results.add(failed(id, null, "The order has no order number."));
                continue;
            }

            InvoiceOrderBuilder.Prepared prepared;
            try {
                prepared = builder.build(order);
            } catch (IllegalArgumentException e) {
                results.add(failed(id, number, e.getMessage()));
                continue;
            }

            try {
                Reply reply = client.send(to, prepared);
                int missing = reply.outcome() == Outcome.SENT ? prepared.missingPhotos() + reply.missingPhotos() : 0;
                if (reply.outcome() == Outcome.FAILED) {
                    results.add(failed(id, number, "Erezer Invoice refused it: "
                            + (reply.message() == null ? "no reason given." : reply.message())));
                    continue;
                }
                remember(order.getId(), reply, missing, sentBy);
                results.add(new SendResult(id, number, reply.outcome(), reply.invoiceOrderId(), null, missing));
            } catch (OrderRefusedException e) {
                results.add(failed(id, number, e.getMessage()));
            } catch (KeyRefusedException | UnreachableException e) {
                stopped = e.getMessage();
                results.add(failed(id, number, stopped));
            }
        }
        log.info("Sent to Erezer Invoice by {}: {}", sentBy, results.stream()
                .map(r -> (r.orderNumber() == null ? r.orderId() : r.orderNumber()) + "=" + r.outcome()).toList());
        return results;
    }

    /** The "In Invoice ✓" marks for these orders; orders never sent are left out. */
    public Map<UUID, SentMark> marks(Collection<UUID> orderIds) {
        Map<UUID, SentMark> marks = new LinkedHashMap<>();
        if (orderIds == null || orderIds.isEmpty()) return marks;
        for (OrderInvoiceSend s : sentRepository.findAllById(orderIds)) {
            marks.put(s.getOrderId(), new SentMark(s.getInvoiceOrderId(), s.getSentAt(), s.getSentByName(), s.getMissingPhotos()));
        }
        return marks;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Connection connection() {
        return settings.connection().orElseThrow(() -> new InvalidRequestException(
                "Erezer Invoice isn't connected yet. Add its address and key in Settings → Erezer Invoice."));
    }

    /** A resend that found the order already there keeps the first send's date and name. */
    private void remember(UUID orderId, Reply reply, int missingPhotos, String sentBy) {
        OrderInvoiceSend existing = sentRepository.findById(orderId).orElse(null);
        if (existing != null && reply.outcome() == Outcome.ALREADY_THERE) {
            if (existing.getInvoiceOrderId() == null && reply.invoiceOrderId() != null) {
                existing.setInvoiceOrderId(reply.invoiceOrderId());
                sentRepository.save(existing);
            }
            return;
        }
        OrderInvoiceSend row = existing != null ? existing : new OrderInvoiceSend();
        row.setOrderId(orderId);
        row.setInvoiceOrderId(reply.invoiceOrderId());
        row.setSentAt(Instant.now());
        row.setSentByName(sentBy == null ? null : sentBy.length() > 150 ? sentBy.substring(0, 150) : sentBy);
        row.setMissingPhotos(missingPhotos);
        sentRepository.save(row);
    }

    private static SendResult failed(UUID id, String number, String message) {
        return new SendResult(id, number, Outcome.FAILED, null, message, 0);
    }
}
