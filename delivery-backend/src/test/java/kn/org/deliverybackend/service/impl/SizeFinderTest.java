package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.SizeFinderSetting;
import kn.org.deliverybackend.enumeration.Fit;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.SizeFinderSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The size finder's rules: standard until changed, one table per fit, checked before they are saved. */
class SizeFinderTest {

    private final SizeFinderSettingRepository repo = mock(SizeFinderSettingRepository.class);
    private final SizeFinderService service = new SizeFinderService(repo);
    private final Map<String, SizeFinderSetting> stored = new HashMap<>();

    @BeforeEach
    void setUp() {
        Fit.install(List.of(Fit.DROP_SHOULDER, Fit.REGULAR_FIT));
        when(repo.findAll()).thenAnswer(inv -> new ArrayList<>(stored.values()));
        when(repo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(stored.get(inv.<String>getArgument(0))));
        when(repo.save(any(SizeFinderSetting.class))).thenAnswer(inv -> {
            SizeFinderSetting s = inv.getArgument(0);
            stored.put(s.getScope(), s);
            return s;
        });
        doAnswer(inv -> stored.remove(inv.<String>getArgument(0))).when(repo).deleteById(anyString());
    }

    @Test
    void untilChangedEveryFitAndTheGeneralTableHoldTheStandardRulesAndAreOn() {
        SizeFinderService.Finder f = service.finder();
        assertTrue(f.enabled());
        assertEquals(SizeFinderService.standardRules(), f.general().rules());
        assertEquals(List.of("DROP_SHOULDER", "REGULAR_FIT"), List.copyOf(f.fits().keySet()));
        assertTrue(f.fits().get("REGULAR_FIT").enabled());
        assertEquals("3XL", f.fits().get("DROP_SHOULDER").rules().get(5).size());
    }

    @Test
    void aFitGetsItsOwnTableAndCanBeSwitchedOffAlone() {
        List<SizeFinderService.Rule> loose = List.of(
                new SizeFinderService.Rule("M", 66, 60), new SizeFinderService.Rule("L", 70, 75), new SizeFinderService.Rule("XL", null, null));
        SizeFinderService.Finder f = service.save("DROP_SHOULDER", null, loose);
        assertEquals(loose, f.fits().get("DROP_SHOULDER").rules());
        assertEquals(SizeFinderService.standardRules(), f.fits().get("REGULAR_FIT").rules(), "the other fit is untouched");

        f = service.save("REGULAR_FIT", false, null);
        assertFalse(f.fits().get("REGULAR_FIT").enabled());
        assertEquals(SizeFinderService.standardRules(), f.fits().get("REGULAR_FIT").rules(), "switching keeps the sizes");

        f = service.save("DROP_SHOULDER", null, List.of());
        assertEquals(SizeFinderService.standardRules(), f.fits().get("DROP_SHOULDER").rules(), "an empty list puts the standard rules back");
    }

    @Test
    void theGeneralTableAndTheWholeShopHaveTheirOwnSwitch() {
        assertFalse(service.save("GENERAL", false, null).general().enabled());
        assertFalse(service.setShopEnabled(false).enabled());
        assertTrue(service.setShopEnabled(true).enabled());
    }

    @Test
    void aTableIsCheckedBeforeItIsSaved() {
        assertThrows(InvalidRequestException.class, () -> service.save("REGULAR_FIT", null, List.of(
                new SizeFinderService.Rule("M", 66, 60), new SizeFinderService.Rule("m", 70, 70))), "a size twice");
        InvalidRequestException smaller = assertThrows(InvalidRequestException.class, () -> service.save("REGULAR_FIT", null, List.of(
                new SizeFinderService.Rule("M", 66, 60), new SizeFinderService.Rule("L", 65, 70))));
        assertTrue(smaller.getMessage().contains("more than the size before"), smaller.getMessage());
        assertThrows(InvalidRequestException.class, () -> service.save("REGULAR_FIT", null, List.of(
                new SizeFinderService.Rule("M", null, 60), new SizeFinderService.Rule("L", 70, 70))), "only the largest may be open");
        assertThrows(InvalidRequestException.class, () -> service.save("REGULAR_FIT", null, List.of(new SizeFinderService.Rule(" ", 66, 60))));
        assertThrows(InvalidRequestException.class, () -> service.save("BAGGY", null, List.of()), "a fit the shop does not have");
        assertTrue(stored.isEmpty(), "nothing was saved");
    }

    @Test
    void aRemovedFitTakesItsTableWithIt() {
        service.save("REGULAR_FIT", false, null);
        service.forgetFit("REGULAR_FIT");
        assertTrue(stored.isEmpty());
    }
}
