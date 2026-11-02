package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.ShowcaseSettings;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.ShowcaseSettingsRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The showcase screen's settings: sensible until changed, and only sensible values saved. */
class ShowcaseSettingsTest {

    private final ShowcaseSettingsRepository repo = mock(ShowcaseSettingsRepository.class);
    private final ShowcaseSettingsService service = new ShowcaseSettingsService(repo);

    @Test
    void untilChangedItPlaysNewArrivalsForSixSecondsEachWithQrCodes() {
        when(repo.findById(ShowcaseSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        ShowcaseSettingsService.Settings s = service.get();
        assertTrue(s.enabled());
        assertEquals(6, s.slideSeconds());
        assertEquals("NEW", s.productMode());
        assertTrue(s.showQr());
    }

    @Test
    void tickedProductsKeepTheirOrderOnceEach() {
        when(repo.findById(ShowcaseSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(repo.save(any(ShowcaseSettings.class))).thenAnswer(inv -> inv.getArgument(0));
        ShowcaseSettingsService.Settings saved = service.save(
                new ShowcaseSettingsService.Settings(true, 8, "picked", Arrays.asList(5L, 2L, 5L, null, 9L), false));
        assertEquals("PICKED", saved.productMode());
        assertEquals(List.of(5L, 2L, 9L), saved.productIds());
        assertEquals(8, saved.slideSeconds());
    }

    @Test
    void nonsenseIsRefused() {
        when(repo.findById(ShowcaseSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        assertThrows(InvalidRequestException.class, () -> service.save(new ShowcaseSettingsService.Settings(true, 1, "NEW", List.of(), true)));
        assertThrows(InvalidRequestException.class, () -> service.save(new ShowcaseSettingsService.Settings(true, 6, "RANDOM", List.of(), true)));
        assertThrows(InvalidRequestException.class, () -> service.save(new ShowcaseSettingsService.Settings(true, 6, "PICKED", List.of(), true)));
    }
}
