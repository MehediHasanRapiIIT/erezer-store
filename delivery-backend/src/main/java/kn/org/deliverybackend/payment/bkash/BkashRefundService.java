package kn.org.deliverybackend.payment.bkash;

import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.Payment;
import kn.org.deliverybackend.entity.PaymentRefund;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.PaymentRefundRepository;
import kn.org.deliverybackend.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends money back to the wallet that paid for a bKash order, through bKash's Refund API:
 * from the order page, or when a return is marked refunded. bKash's rules, and the shop's:
 * <ul>
 *   <li>only a completed bKash payment, and only within 60 days of it;</li>
 *   <li>never more than was paid, all refunds together; at most 10 refunds a payment
 *       (only one on bKash's v1.2.0-beta API);</li>
 *   <li>an answer with a refund already on record is not a new refund: v1.2.0-beta, asked
 *       to refund a payment again, answers with its first refund instead;</li>
 *   <li>one refund of an order at a time, so a double click cannot pay twice;</li>
 *   <li>every try is kept, with who asked and bKash's answer. A try bKash did not answer
 *       counts as refunded until checked, so it can never be paid out a second time.</li>
 * </ul>
 * Records are written in their own transaction: once bKash has paid out, the record
 * stays even if whatever asked for the refund fails afterwards.
 */
@Service
@Slf4j
public class BkashRefundService {

    public static final int REFUND_DAYS = 60;
    private static final String UNKNOWN = "UNKNOWN";

    private final PaymentRepository payments;
    private final PaymentRefundRepository refunds;
    private final OrderRepository orders;
    private final BkashPaymentService bkash;
    private final TransactionTemplate ownTransaction;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Object> locks = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public BkashRefundService(PaymentRepository payments, PaymentRefundRepository refunds, OrderRepository orders,
                              BkashPaymentService bkash, PlatformTransactionManager transactions) {
        this(payments, refunds, orders, bkash, transactions, Clock.systemDefaultZone());
    }

    BkashRefundService(PaymentRepository payments, PaymentRefundRepository refunds, OrderRepository orders,
                       BkashPaymentService bkash, PlatformTransactionManager transactions, Clock clock) {
        this.payments = payments;
        this.refunds = refunds;
        this.orders = orders;
        this.bkash = bkash;
        this.ownTransaction = new TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** One refund as staff see it. */
    public record Line(String status, BigDecimal amount, String reason, String refundTrxId, String message,
                       String requestedBy, LocalDateTime at, boolean fromReturn) {
    }

    /**
     * What can still be refunded: paid, refunded so far, what is left, until when, and
     * why not when it can't (no bKash payment, past 60 days, 10 refunds already).
     */
    public record Summary(BigDecimal paid, BigDecimal refunded, BigDecimal refundable, LocalDateTime refundableUntil,
                          boolean allowed, String notAllowedReason, String trxId, List<Line> refunds, int maxRefunds) {
    }

    public Summary summary(UUID orderId) {
        List<PaymentRefund> all = refunds.findByOrderIdOrderByCreatedAtAsc(orderId);
        List<Line> lines = all.stream().map(r -> new Line(r.getStatus(), r.getAmount(), r.getReason(), r.getRefundTrxId(),
                r.getMessage(), r.getRequestedBy(), r.getCreatedAt(), r.getReturnRequestId() != null)).toList();
        Payment paid = BkashPaymentState.completed(payments.findByOrderId(orderId)).orElse(null);
        if (paid == null) {
            return new Summary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, false,
                    "This order has no completed bKash payment.", null, lines, bkash.maxRefunds());
        }
        BigDecimal refunded = counted(all);
        BigDecimal left = paid.getAmount().subtract(refunded).max(BigDecimal.ZERO);
        LocalDateTime until = paidAt(paid).plusDays(REFUND_DAYS);
        String why = null;
        if (all.stream().anyMatch(r -> UNKNOWN.equals(r.getStatus()))) {
            why = "bKash did not answer an earlier refund. Check the bKash merchant account before refunding more.";
        } else if (left.signum() <= 0) {
            why = "The whole payment has been refunded.";
        } else if (LocalDateTime.now(clock).isAfter(until)) {
            why = "bKash refunds are only possible within " + REFUND_DAYS + " days of the payment.";
        } else if (completedCount(all) >= bkash.maxRefunds()) {
            why = bkash.maxRefunds() == 1 ? "bKash allows only one refund of each payment on this account."
                    : "bKash allows at most " + bkash.maxRefunds() + " refunds of one payment.";
        }
        return new Summary(paid.getAmount(), refunded, left, until, why == null, why, paid.getProviderTrxId(), lines,
                bkash.maxRefunds());
    }

    /**
     * Refunds part or all of an order's bKash payment. Answers the completed refund;
     * throws with bKash's own words when bKash refuses, or the rule broken.
     */
    public PaymentRefund refund(UUID orderId, BigDecimal amount, String reason, String requestedBy, UUID returnId) {
        Object lock = locks.computeIfAbsent(orderId, k -> new Object());
        synchronized (lock) {
            Order order = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found."));
            BigDecimal money = checkedAmount(amount);
            String why = reason == null || reason.isBlank() ? "Refund" : reason.trim();
            if (why.length() > 255) throw new InvalidRequestException("Keep the reason under 255 letters.");

            Summary now = summary(orderId);
            if (!now.allowed()) throw new InvalidRequestException(now.notAllowedReason());
            if (money.compareTo(now.refundable()) > 0) {
                throw new InvalidRequestException("At most ৳" + now.refundable().toPlainString() + " can still be refunded for this order.");
            }
            Payment paid = BkashPaymentState.completed(payments.findByOrderId(orderId)).orElseThrow();
            String sku = order.getOrderNumber() != null ? order.getOrderNumber() : orderId.toString().substring(0, 8);

            BkashRefundResponse answer = bkash.refund(paid.getProviderPaymentId(), paid.getProviderTrxId(), money, sku, why);
            if (answer.completed() && alreadyRecorded(paid, answer.getRefundTrxId())) {
                answer = BkashRefundResponse.builder().status("Failed").errorMessage("bKash made no new refund: it answered with the earlier refund "
                        + answer.getRefundTrxId() + ". Only one refund of this payment is possible.").build();
            }

            PaymentRefund record = new PaymentRefund();
            record.setPaymentId(paid.getId());
            record.setOrderId(orderId);
            record.setReturnRequestId(returnId);
            record.setAmount(answer.completed() && answer.getAmount() != null ? answer.getAmount() : money);
            record.setReason(why);
            record.setRequestedBy(requestedBy);
            record.setRefundTrxId(answer.getRefundTrxId());
            record.setStatus(answer.completed() ? PaymentRefund.COMPLETED
                    : "Unknown".equalsIgnoreCase(answer.getStatus()) ? UNKNOWN : PaymentRefund.FAILED);
            record.setMessage(answer.getErrorMessage() == null ? null : cut(answer.getErrorMessage(), 500));
            ownTransaction.executeWithoutResult(t -> refunds.save(record));
            log.info("[bKash] Refund {} of ৳{} for order {} by {}: {} {}", record.getStatus(), record.getAmount(),
                    sku, requestedBy, record.getRefundTrxId() == null ? "" : record.getRefundTrxId(),
                    record.getMessage() == null ? "" : record.getMessage());

            if (!answer.completed()) {
                throw new InvalidRequestException(answer.getErrorMessage() == null ? "bKash did not make the refund." : answer.getErrorMessage());
            }
            return record;
        }
    }

    /** Completed refunds, and any bKash did not answer: they may have been paid out. */
    private static BigDecimal counted(List<PaymentRefund> all) {
        return all.stream().filter(r -> PaymentRefund.COMPLETED.equals(r.getStatus()) || UNKNOWN.equals(r.getStatus()))
                .map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean alreadyRecorded(Payment paid, String refundTrxId) {
        return refundTrxId != null && refunds.findByPaymentId(paid.getId()).stream()
                .anyMatch(r -> PaymentRefund.COMPLETED.equals(r.getStatus()) && refundTrxId.equals(r.getRefundTrxId()));
    }

    private static long completedCount(List<PaymentRefund> all) {
        return all.stream().filter(r -> PaymentRefund.COMPLETED.equals(r.getStatus())).count();
    }

    private static BigDecimal checkedAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new InvalidRequestException("Enter how much to refund.");
        if (amount.stripTrailingZeros().scale() > 2) throw new InvalidRequestException("Use at most 2 decimal places (paisa).");
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** When bKash confirmed the payment: the payment row was last changed then. */
    private static LocalDateTime paidAt(Payment p) {
        java.util.Date at = p.getUpdatedAt() != null ? p.getUpdatedAt() : p.getCreatedAt();
        return at == null ? LocalDateTime.MIN : LocalDateTime.ofInstant(at.toInstant(), ZoneId.systemDefault());
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
