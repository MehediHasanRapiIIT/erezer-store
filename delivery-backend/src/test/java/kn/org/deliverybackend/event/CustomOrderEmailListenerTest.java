package kn.org.deliverybackend.event;

import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import kn.org.deliverybackend.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A new custom design request reaches a real mailbox, never the placeholder that bounces. */
class CustomOrderEmailListenerTest {

    private final StoreSettingsRepository settings = mock(StoreSettingsRepository.class);
    private final CustomOrderEmailListener listener = new CustomOrderEmailListener(mock(EmailService.class), settings);

    private void supportEmail(String address) {
        StoreSettings shop = new StoreSettings();
        shop.setSupportEmail(address);
        when(settings.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.of(shop));
    }

    @Test
    void thePlaceholderGivesWayToTheSupportEmailInSettings() {
        ReflectionTestUtils.setField(listener, "adminEmail", "custom@erezer.local");
        supportEmail("support@erezer.com");
        assertEquals("support@erezer.com", listener.recipient());
    }

    @Test
    void aRealAddressOnTheServerIsUsed() {
        ReflectionTestUtils.setField(listener, "adminEmail", "orders@erezer.com");
        supportEmail("support@erezer.com");
        assertEquals("orders@erezer.com", listener.recipient());
    }

    @Test
    void withNoUsableAddressNothingIsSent() {
        ReflectionTestUtils.setField(listener, "adminEmail", "");
        supportEmail(" not an address ");
        assertNull(listener.recipient());
    }
}
