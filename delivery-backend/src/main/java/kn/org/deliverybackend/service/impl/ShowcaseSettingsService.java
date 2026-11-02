package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.ShowcaseSettings;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.ShowcaseSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The Settings page's "Showcase screen" section: the in-store screen at /showcase
 * that plays the shop's products, bundles and flash sales by itself, like an advert.
 */
@Service
@RequiredArgsConstructor
public class ShowcaseSettingsService {

    public static final int MIN_SECONDS = 3, MAX_SECONDS = 30, MAX_PICKED = 60;
    private static final Set<String> MODES = Set.of("NEW", "ALL", "PICKED");

    private final ShowcaseSettingsRepository repository;

    /**
     * The section as the admin page edits it and the screen reads it.
     * productMode: NEW (new arrivals first), ALL, or PICKED (only productIds, in that order).
     */
    public record Settings(boolean enabled, int slideSeconds, String productMode, List<Long> productIds, boolean showQr) {
    }

    @Transactional(readOnly = true)
    public Settings get() {
        return toSettings(repository.findById(ShowcaseSettings.SINGLETON_ID).orElseGet(ShowcaseSettings::new));
    }

    @Transactional
    public Settings save(Settings change) {
        if (change == null) throw new InvalidRequestException("Nothing to save.");
        if (change.slideSeconds() < MIN_SECONDS || change.slideSeconds() > MAX_SECONDS) {
            throw new InvalidRequestException("Each slide can show for " + MIN_SECONDS + " to " + MAX_SECONDS + " seconds.");
        }
        String mode = change.productMode() == null ? "NEW" : change.productMode().trim().toUpperCase(Locale.ROOT);
        if (!MODES.contains(mode)) throw new InvalidRequestException("Choose which products the showcase shows.");
        List<Long> ids = change.productIds() == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(change.productIds().stream().filter(Objects::nonNull).toList()));
        if (ids.size() > MAX_PICKED) throw new InvalidRequestException("Tick at most " + MAX_PICKED + " products.");
        if ("PICKED".equals(mode) && ids.isEmpty()) throw new InvalidRequestException("Tick at least one product to show.");

        ShowcaseSettings row = repository.findById(ShowcaseSettings.SINGLETON_ID).orElseGet(ShowcaseSettings::new);
        row.setEnabled(change.enabled());
        row.setSlideSeconds(change.slideSeconds());
        row.setProductMode(mode);
        row.setProductIds(ids.isEmpty() ? null : String.join(",", ids.stream().map(String::valueOf).toList()));
        row.setShowQr(change.showQr());
        return toSettings(repository.save(row));
    }

    private static Settings toSettings(ShowcaseSettings row) {
        List<Long> ids = row.getProductIds() == null || row.getProductIds().isBlank() ? List.of()
                : Arrays.stream(row.getProductIds().split(",")).map(String::trim)
                        .filter(s -> s.matches("\\d+")).map(Long::valueOf).toList();
        return new Settings(!Boolean.FALSE.equals(row.getEnabled()),
                row.getSlideSeconds() == null ? 6 : row.getSlideSeconds(),
                row.getProductMode() == null ? "NEW" : row.getProductMode(), ids, !Boolean.FALSE.equals(row.getShowQr()));
    }
}
