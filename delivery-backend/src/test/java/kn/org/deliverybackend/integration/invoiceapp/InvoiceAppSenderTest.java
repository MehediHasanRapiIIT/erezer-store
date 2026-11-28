package kn.org.deliverybackend.integration.invoiceapp;

import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.OrderInvoiceSend;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppClient.Reply;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.Outcome;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.SendResult;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppSettingsService.Connection;
import kn.org.deliverybackend.repository.OrderInvoiceSendRepository;
import kn.org.deliverybackend.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sending the chosen orders: what goes, what doesn't, and what is remembered. */
class InvoiceAppSenderTest {

    private final InvoiceAppSettingsService settings = mock(InvoiceAppSettingsService.class);
    private final InvoiceOrderBuilder builder = mock(InvoiceOrderBuilder.class);
    private final InvoiceAppClient client = mock(InvoiceAppClient.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderInvoiceSendRepository sent = mock(OrderInvoiceSendRepository.class);
    private final InvoiceAppSender sender = new InvoiceAppSender(settings, builder, client, orders, sent);

    private final Connection to = new Connection("http://invoice.test", "ezs_key");

    @BeforeEach
    void connected() {
        when(settings.connection()).thenReturn(Optional.of(to));
        when(sent.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void notConnectedSendsNothing() {
        when(settings.connection()).thenReturn(Optional.empty());
        InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> sender.send(List.of(UUID.randomUUID()), "Admin"));
        assertEquals("Erezer Invoice isn't connected yet. Add its address and key in Settings → Erezer Invoice.", e.getMessage());
    }

    @Test
    void eachOrderGetsItsOwnResultAndTheSentOnesAreRemembered() {
        Order placed = order("EZ-1", "PLACED");
        Order cancelled = order("EZ-2", "CANCELLED");
        Order there = order("EZ-3", "DELIVERED");
        UUID gone = UUID.randomUUID();
        InvoiceOrderBuilder.Prepared prepared = prepared(1);
        when(builder.build(any())).thenReturn(prepared);
        when(client.send(to, prepared))
                .thenReturn(new Reply(Outcome.SENT, 152L, null, 0))
                .thenReturn(new Reply(Outcome.ALREADY_THERE, 90L, null, 0));

        List<SendResult> results = sender.send(List.of(placed.getId(), cancelled.getId(), there.getId(), gone, placed.getId()), "Rapi");

        assertEquals(4, results.size(), "a repeated id is sent once");
        assertEquals(Outcome.SENT, results.get(0).outcome());
        assertEquals(152L, results.get(0).invoiceOrderId());
        assertEquals(1, results.get(0).missingPhotos());
        assertEquals("Cancelled orders can't be sent.", results.get(1).message());
        assertEquals(Outcome.ALREADY_THERE, results.get(2).outcome());
        assertEquals("The order wasn't found.", results.get(3).message());

        ArgumentCaptor<OrderInvoiceSend> saved = ArgumentCaptor.forClass(OrderInvoiceSend.class);
        verify(sent, times(2)).save(saved.capture());
        assertEquals(placed.getId(), saved.getAllValues().get(0).getOrderId());
        assertEquals("Rapi", saved.getAllValues().get(0).getSentByName());
        assertEquals(1, saved.getAllValues().get(0).getMissingPhotos());
        assertEquals(90L, saved.getAllValues().get(1).getInvoiceOrderId());
    }

    @Test
    void aRefusedKeyStopsTheRestWithoutTryingThem() {
        Order a = order("EZ-1", "PLACED");
        Order b = order("EZ-2", "PLACED");
        InvoiceOrderBuilder.Prepared prepared = prepared(0);
        when(builder.build(any())).thenReturn(prepared);
        when(client.send(to, prepared)).thenThrow(keyRefused());

        List<SendResult> results = sender.send(List.of(a.getId(), b.getId()), "Rapi");

        assertEquals(Outcome.FAILED, results.get(0).outcome());
        assertEquals(results.get(0).message(), results.get(1).message());
        verify(client, times(1)).send(eq(to), any());
        verify(sent, never()).save(any());
    }

    @Test
    void anOrderThatCantBeBuiltIsExplained() {
        Order a = order("EZ-1", "PLACED");
        when(builder.build(a)).thenThrow(new IllegalArgumentException("The order has no phone number."));
        SendResult r = sender.send(List.of(a.getId()), "Rapi").get(0);
        assertEquals(Outcome.FAILED, r.outcome());
        assertEquals("The order has no phone number.", r.message());
        assertNull(r.invoiceOrderId());
    }

    @Test
    void aResendKeepsTheFirstSendsDateAndName() {
        Order a = order("EZ-1", "PLACED");
        OrderInvoiceSend first = new OrderInvoiceSend();
        first.setOrderId(a.getId());
        first.setInvoiceOrderId(152L);
        first.setSentAt(Instant.parse("2026-11-20T08:30:00Z"));
        first.setSentByName("Owner");
        when(sent.findById(a.getId())).thenReturn(Optional.of(first));
        InvoiceOrderBuilder.Prepared prepared = prepared(0);
        when(builder.build(a)).thenReturn(prepared);
        when(client.send(to, prepared)).thenReturn(new Reply(Outcome.ALREADY_THERE, 152L, null, 0));

        sender.send(List.of(a.getId()), "Rapi");

        verify(sent, never()).save(any());
        assertEquals("Owner", first.getSentByName());
    }

    // ── helpers ──

    private Order order(String number, String status) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setOrderNumber(number);
        o.setOrderStatus(status);
        o.setTotalAmount(BigDecimal.TEN);
        when(orders.findById(o.getId())).thenReturn(Optional.of(o));
        return o;
    }

    private static InvoiceOrderBuilder.Prepared prepared(int missing) {
        return new InvoiceOrderBuilder.Prepared(null, List.of(), missing);
    }

    private static InvoiceAppClient.KeyRefusedException keyRefused() {
        return new InvoiceAppClient.KeyRefusedException();
    }
}
