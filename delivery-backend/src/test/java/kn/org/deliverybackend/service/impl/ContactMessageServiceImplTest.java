package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.ContactMessageRepository;
import kn.org.deliverybackend.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The Order ID on the contact form, typed the way customers really type it. */
class ContactMessageServiceImplTest {

    private static final String ORDER = "4a35228f-1c2d-4e5f-8a9b-0c1d2e3f4a5b";

    private final OrderRepository orders = mock(OrderRepository.class);
    private final ContactMessageServiceImpl service =
            new ContactMessageServiceImpl(mock(ContactMessageRepository.class), orders);

    @Test
    void theFullIdCopiedFromTheOrdersPageWithItsHash() {
        when(orders.findIdsStartingWith(ORDER)).thenReturn(List.of(ORDER));

        assertEquals(UUID.fromString(ORDER), service.resolveOrderId("  #" + ORDER + " "));
    }

    @Test
    void theShortIdFromAnSmsInCapitals() {
        when(orders.findIdsStartingWith("4a35228f")).thenReturn(List.of(ORDER));

        assertEquals(UUID.fromString(ORDER), service.resolveOrderId("#4A35228F"));
    }

    @Test
    void blankMeansNoOrder() {
        assertNull(service.resolveOrderId(null));
        assertNull(service.resolveOrderId("   "));
        assertNull(service.resolveOrderId("#"));
        verify(orders, never()).findIdsStartingWith(anyString());
    }

    @Test
    void textThatIsNotAnOrderIdSaysSoWithoutQueryingOrLettingWildcardsIn() {
        for (String typed : List.of("my blue hoodie", "4a35", "4a35%", "____________")) {
            InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                    () -> service.resolveOrderId(typed));
            assertTrue(ex.getMessage().contains("couldn't find an order"), ex.getMessage());
        }
        verify(orders, never()).findIdsStartingWith(anyString());
    }

    @Test
    void anIdThatMatchesNoOrder() {
        when(orders.findIdsStartingWith("deadbeef")).thenReturn(List.of());

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> service.resolveOrderId("#deadbeef"));
        assertTrue(ex.getMessage().contains("\"#deadbeef\""), ex.getMessage());
    }

    @Test
    void aPrefixSharedByTwoOrdersAsksForMore() {
        when(orders.findIdsStartingWith("4a35228f")).thenReturn(List.of(ORDER, "4a35228f-0000-0000-0000-000000000000"));

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> service.resolveOrderId("4a35228f"));
        assertTrue(ex.getMessage().contains("More than one order"), ex.getMessage());
    }
}
