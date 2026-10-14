package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.CustomerEmailSettings;
import kn.org.deliverybackend.enumeration.OrderStatus;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.CustomerEmailSettingsRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Fewer, clearer customer emails: the settings, the plain-text copy, and the steps an order goes through. */
class CustomerEmailsTest {

    private final CustomerEmailSettingsRepository repo = mock(CustomerEmailSettingsRepository.class);
    private final CustomerEmailSettingsService service = new CustomerEmailSettingsService(repo);

    @Test
    void untilChangedTheCartReminderAndNewsletterAreOffAndWebsiteReviewsAreOn() {
        when(repo.findById(CustomerEmailSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        CustomerEmailSettingsService.Settings s = service.get();
        assertTrue(s.reviewOnWebsite());
        assertFalse(s.cartReminderEnabled());
        assertFalse(s.newsletterEnabled());
        assertFalse(service.cartReminderEnabled());
        assertFalse(service.newsletterEnabled());
    }

    @Test
    void reviewLinksAreSavedAsSafeWebAddresses() {
        when(repo.findById(CustomerEmailSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(repo.save(any(CustomerEmailSettings.class))).thenAnswer(inv -> inv.getArgument(0));
        CustomerEmailSettingsService.Settings saved = service.save(new CustomerEmailSettingsService.Settings(
                true, "m.me/erezer", " https://ig.me/m/erezer ", "01712-345678", "Dhanmondi, Dhaka", false, true));
        assertEquals("https://m.me/erezer", saved.facebookUrl());
        assertEquals("https://ig.me/m/erezer", saved.instagramUrl());
        assertTrue(saved.newsletterEnabled());

        assertThrows(InvalidRequestException.class, () -> service.save(new CustomerEmailSettingsService.Settings(
                true, "http://m.me/erezer", "", "", "", false, false)), "plain http is refused");
        assertThrows(InvalidRequestException.class, () -> service.save(new CustomerEmailSettingsService.Settings(
                true, "", "", "call me", "", false, false)), "not a phone number");
    }

    @Test
    void aWhatsAppNumberBecomesAChatLink() {
        assertEquals("https://wa.me/8801712345678", CustomerEmailSettingsService.whatsappLink("01712-345678"));
        assertEquals("https://wa.me/8801712345678", CustomerEmailSettingsService.whatsappLink("+880 1712 345678"));
        assertEquals(null, CustomerEmailSettingsService.whatsappLink(" "));
    }

    @Test
    void everyEmailHasAPlainTextCopyWithItsLinksWrittenOut() {
        String text = EmailText.plain("<html><head><style>p{}</style></head><body>"
                + "<h1>Your order is on its way</h1><p>Hi <span>Mehedi</span>,</p>"
                + "<table><tr><td>Courier</td><td>Steadfast</td></tr></table>"
                + "<a href=\"https://steadfast.com.bd/t/123\">Track your parcel</a>"
                + "<img src=\"logo.png\" alt=\"EREZER\"/></body></html>");
        assertTrue(text.startsWith("Your order is on its way\n"), text);
        assertTrue(text.contains("Hi Mehedi,"), text);
        assertTrue(text.contains("Courier Steadfast"), text);
        assertTrue(text.contains("Track your parcel: https://steadfast.com.bd/t/123"), text);
        assertFalse(text.contains("<"), text);
    }

    @Test
    void inProductionIsNoLongerAStep() {
        assertTrue(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.PROCESSING));
        assertFalse(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.IN_PRODUCTION));
        assertTrue(OrderStatus.IN_PRODUCTION.canTransitionTo(OrderStatus.PROCESSING), "an older order standing there can move on");
    }

    @Test
    void aTrackingLinkMustBeASafeWebAddress() {
        assertEquals("https://steadfast.com.bd/t/123", OrderHistoryServiceImpl.trackingLink("steadfast.com.bd/t/123"));
        assertEquals(null, OrderHistoryServiceImpl.trackingLink(""));
        assertThrows(RuntimeException.class, () -> OrderHistoryServiceImpl.trackingLink("javascript:alert(1)"));
    }
}
