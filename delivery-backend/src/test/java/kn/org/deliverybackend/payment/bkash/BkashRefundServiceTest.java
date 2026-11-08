package kn.org.deliverybackend.payment.bkash;

import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.Payment;
import kn.org.deliverybackend.entity.PaymentRefund;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.PaymentRefundRepository;
import kn.org.deliverybackend.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** bKash refunds: never more than was paid, never after 60 days, never twice for one try bKash did not answer. */
class BkashRefundServiceTest {

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final BkashPaymentService bkash = mock(BkashPaymentService.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final List<PaymentRefund> stored = new ArrayList<>();
    private final UUID orderId = UUID.randomUUID();
    private final Instant paidAt = Instant.parse("2026-11-01T10:00:00Z");
    private Payment paid;

    private BkashRefundService at(String now) {
        return new BkashRefundService(payments, refunds, orders, bkash, tx, Clock.fixed(Instant.parse(now), ZoneId.systemDefault()));
    }

    @BeforeEach
    void setUp() {
        Order order = new Order();
        order.setId(orderId);
        order.setOrderNumber("EZ-1001");
        order.setPaymentMethod("BKASH");
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        paid = new Payment();
        paid.setId(UUID.randomUUID());
        paid.setOrderId(orderId);
        paid.setProvider("BKASH");
        paid.setStatus("Completed");
        paid.setAmount(new BigDecimal("460.00"));
        paid.setProviderPaymentId("TR001");
        paid.setProviderTrxId("DK810PV2KN");
        paid.setCreatedAt(Date.from(paidAt));
        paid.setUpdatedAt(Date.from(paidAt));
        when(payments.findByOrderId(orderId)).thenReturn(List.of(paid));
        when(refunds.findByOrderIdOrderByCreatedAtAsc(orderId)).thenAnswer(inv -> new ArrayList<>(stored));
        when(refunds.save(any(PaymentRefund.class))).thenAnswer(inv -> { stored.add(inv.getArgument(0)); return inv.getArgument(0); });
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private void bkashSays(String status, String message) {
        when(bkash.refund(anyString(), anyString(), any(), anyString(), anyString())).thenAnswer(inv ->
                BkashRefundResponse.builder().status(status).amount(inv.getArgument(2))
                        .refundTrxId("Completed".equals(status) ? "R" + stored.size() : null).errorMessage(message).build());
    }

    @Test
    void partRefundsAddUpToNoMoreThanWasPaid() {
        bkashSays("Completed", null);
        BkashRefundService service = at("2026-11-08T10:00:00Z");
        PaymentRefund first = service.refund(orderId, new BigDecimal("400"), "Wrong size", "admin:mehedi", null);
        assertEquals(PaymentRefund.COMPLETED, first.getStatus());
        assertEquals("admin:mehedi", first.getRequestedBy());
        assertEquals(0, new BigDecimal("60.00").compareTo(service.summary(orderId).refundable()));

        InvalidRequestException tooMuch = assertThrows(InvalidRequestException.class,
                () -> service.refund(orderId, new BigDecimal("60.01"), "x", "admin", null));
        assertTrue(tooMuch.getMessage().contains("60.00"));
        service.refund(orderId, new BigDecimal("60"), "Shipping back", "admin", null);
        BkashRefundService.Summary done = service.summary(orderId);
        assertFalse(done.allowed());
        assertEquals("The whole payment has been refunded.", done.notAllowedReason());
    }

    @Test
    void notAfterSixtyDays() {
        bkashSays("Completed", null);
        BkashRefundService service = at("2026-12-31T11:00:00Z");
        assertThrows(InvalidRequestException.class, () -> service.refund(orderId, new BigDecimal("10"), "x", "admin", null));
        verify(bkash, never()).refund(anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void aRefusalIsKeptAndShownButNotCounted() {
        bkashSays("Failed", "Insufficient Balance");
        BkashRefundService service = at("2026-11-08T10:00:00Z");
        InvalidRequestException refused = assertThrows(InvalidRequestException.class,
                () -> service.refund(orderId, new BigDecimal("100"), "x", "admin", null));
        assertEquals("Insufficient Balance", refused.getMessage());
        assertEquals(1, stored.size());
        assertEquals(PaymentRefund.FAILED, stored.get(0).getStatus());
        assertEquals(0, new BigDecimal("460.00").compareTo(service.summary(orderId).refundable()));
    }

    @Test
    void noAnswerBlocksAnotherRefundUntilChecked() {
        bkashSays("Unknown", "bKash did not answer. Check the bKash merchant account before trying again.");
        BkashRefundService service = at("2026-11-08T10:00:00Z");
        assertThrows(InvalidRequestException.class, () -> service.refund(orderId, new BigDecimal("100"), "x", "admin", null));
        BkashRefundService.Summary after = service.summary(orderId);
        assertFalse(after.allowed(), "it may have been paid out, so nothing more until someone checks");
        assertEquals(0, new BigDecimal("360.00").compareTo(after.refundable()));
    }

    @Test
    void nothingToRefundWithoutABkashPayment() {
        paid.setStatus("Failed");
        BkashRefundService service = at("2026-11-08T10:00:00Z");
        assertThrows(InvalidRequestException.class, () -> service.refund(orderId, new BigDecimal("10"), "x", "admin", null));
    }

    @Test
    void amountsAreTakaAndPaisaOnly() {
        bkashSays("Completed", null);
        BkashRefundService service = at("2026-11-08T10:00:00Z");
        assertThrows(InvalidRequestException.class, () -> service.refund(orderId, new BigDecimal("0"), "x", "admin", null));
        assertThrows(InvalidRequestException.class, () -> service.refund(orderId, new BigDecimal("1.234"), "x", "admin", null));
    }
}
