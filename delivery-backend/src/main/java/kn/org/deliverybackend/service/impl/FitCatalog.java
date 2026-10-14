package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.FitType;
import kn.org.deliverybackend.enumeration.Fit;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.FitTypeRepository;
import kn.org.deliverybackend.repository.ProductFitSizeChartRepository;
import kn.org.deliverybackend.repository.SizeChartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The shop's list of fits (Drop Shoulder, Regular Fit, Oversized…), managed on
 * the Size Charts page: add one, rename it, give it a size chart, put the list
 * in order, hide a fit or remove one nobody uses.
 *
 * <p>After every change the list is handed to {@link Fit}, which the rest of the
 * program reads; it is read once when the server starts too.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FitCatalog {

    private static final int MAX_FITS = 20;

    private final FitTypeRepository fits;
    private final SizeChartRepository sizeCharts;
    private final ProductFitSizeChartRepository productCharts;
    private final kn.org.deliverybackend.repository.CategoryFitSizeChartRepository categoryCharts;
    private final kn.org.deliverybackend.repository.SizeFinderSettingRepository sizeFinder;

    /** One fit as the admin panel and the shop read it. */
    public record FitView(Long id, String code, String name, String skuCode, Long sizeChartId, String sizeChartName,
                          int position, boolean hidden, long productCount) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStart() {
        try {
            refresh();
        } catch (RuntimeException e) {
            // The two built-in fits stay in place, so the shop keeps working.
            log.warn("Could not read the fit list; using the built-in fits: {}", e.getMessage());
        }
    }

    /** Every fit, in order; hidden ones only when asked for. */
    @Transactional(readOnly = true)
    public List<FitView> list(boolean withHidden) {
        return list(withHidden, withHidden);
    }

    /** Every fit, in order, hidden ones (marked) when asked for, and how many products use each when asked for. */
    @Transactional(readOnly = true)
    public List<FitView> list(boolean withHidden, boolean withCounts) {
        List<FitView> out = new ArrayList<>();
        int position = 0;
        for (FitType f : fits.findAllInOrder()) {
            position++;
            if (!withHidden && Boolean.TRUE.equals(f.getHidden())) continue;
            String chartName = f.getSizeChartId() == null ? null
                    : sizeCharts.findLive(f.getSizeChartId()).map(c -> c.getName()).orElse(null);
            out.add(new FitView(f.getId(), f.getCode(), f.getName(), f.getSkuCode(),
                    chartName == null ? null : f.getSizeChartId(), chartName, position, Boolean.TRUE.equals(f.getHidden()),
                    withCounts ? fits.countProductsIn(f.getCode()) : 0));
        }
        return out;
    }

    @Transactional
    public FitView create(String name, Long sizeChartId) {
        String label = cleanName(name);
        List<FitType> all = fits.findAllInOrder();
        if (all.size() >= MAX_FITS) throw new InvalidRequestException("The shop can have at most " + MAX_FITS + " fits.");
        if (all.stream().anyMatch(f -> f.getName().equalsIgnoreCase(label))) {
            throw new InvalidRequestException("There is already a fit called " + label + ".");
        }
        FitType fit = new FitType();
        fit.setCode(uniqueCode(label));
        fit.setName(label);
        fit.setSkuCode(skuCodeFor(label));
        fit.setSizeChartId(checkedChart(sizeChartId));
        fit.setSortOrder(all.stream().mapToInt(f -> f.getSortOrder() == null ? 0 : f.getSortOrder()).max().orElse(0) + 1);
        fit.setHidden(false);
        fit.setCreatedAt(LocalDateTime.now());
        fit.setUpdatedAt(LocalDateTime.now());
        FitType saved = fits.save(fit);
        refresh();
        return view(saved);
    }

    /** Renames a fit, gives it a chart (0: none) and shows or hides it. A null leaves that part as it is. */
    @Transactional
    public FitView update(Long id, String name, Long sizeChartId, Boolean hidden) {
        FitType fit = find(id);
        if (name != null) {
            String label = cleanName(name);
            if (fits.findAllInOrder().stream().anyMatch(f -> !f.getId().equals(id) && f.getName().equalsIgnoreCase(label))) {
                throw new InvalidRequestException("There is already a fit called " + label + ".");
            }
            fit.setName(label);
        }
        // Null leaves the chart as it is; 0 takes it away.
        if (sizeChartId != null) fit.setSizeChartId(checkedChart(sizeChartId));
        if (hidden != null) {
            if (hidden && !Boolean.TRUE.equals(fit.getHidden())
                    && fits.findAllInOrder().stream().filter(f -> !Boolean.TRUE.equals(f.getHidden())).count() <= 1) {
                throw new InvalidRequestException("At least one fit has to stay offered.");
            }
            fit.setHidden(hidden);
        }
        fit.setUpdatedAt(LocalDateTime.now());
        FitType saved = fits.save(fit);
        refresh();
        return view(saved);
    }

    /** Removes a fit no product uses. One in use can only be hidden. */
    @Transactional
    public void delete(Long id) {
        FitType fit = find(id);
        long using = fits.countProductsIn(fit.getCode());
        if (using > 0) {
            throw new InvalidRequestException(fit.getName() + " is used by " + using + (using == 1 ? " product" : " products")
                    + ", so it cannot be removed. Hide it instead: it is then not offered for new products.");
        }
        if (fits.findAllInOrder().stream().filter(f -> !Boolean.TRUE.equals(f.getHidden())).count() <= 1
                && !Boolean.TRUE.equals(fit.getHidden())) {
            throw new InvalidRequestException("At least one fit has to stay offered.");
        }
        productCharts.clearFit(fit.getCode());
        categoryCharts.clearFit(fit.getCode());
        sizeFinder.findById(fit.getCode()).ifPresent(sizeFinder::delete);
        fits.delete(fit);
        refresh();
    }

    /** Puts the fits in the order given (ids first to last). Fits left out keep their order after them. */
    @Transactional
    public List<FitView> reorder(List<Long> ids) {
        List<FitType> all = fits.findAllInOrder();
        Map<Long, FitType> byId = new LinkedHashMap<>();
        for (FitType f : all) byId.put(f.getId(), f);
        List<Long> order = new ArrayList<>(new LinkedHashSet<>(ids == null ? List.<Long>of() : ids));
        for (Long id : order) {
            if (!byId.containsKey(id)) throw new InvalidRequestException("A fit in the list no longer exists. Reload the page.");
        }
        for (FitType f : all) if (!order.contains(f.getId())) order.add(f.getId());
        for (int i = 0; i < order.size(); i++) {
            FitType f = byId.get(order.get(i));
            if (f.getSortOrder() == null || f.getSortOrder() != i + 1) {
                f.setSortOrder(i + 1);
                fits.save(f);
            }
        }
        refresh();
        return list(true);
    }

    /** Reads the list into {@link Fit}. */
    @Transactional(readOnly = true)
    public void refresh() {
        List<FitType> all = fits.findAllInOrder();
        if (all.isEmpty()) return;   // keep the built-in two
        List<Fit> list = new ArrayList<>();
        for (FitType f : all) {
            Long chart = f.getSizeChartId() == null ? null
                    : sizeCharts.findLive(f.getSizeChartId()).map(c -> c.getId()).orElse(null);
            list.add(new Fit(f.getCode(), f.getName(), f.getSkuCode(), 0, chart, Boolean.TRUE.equals(f.getHidden())));
        }
        Fit.install(list);
    }

    // ── small things ──────────────────────────────────────────────────────────

    private FitType find(Long id) {
        return fits.findById(id).orElseThrow(() -> new ResourceNotFoundException("Fit not found: " + id));
    }

    private FitView view(FitType f) {
        return list(true).stream().filter(v -> v.id().equals(f.getId())).findFirst().orElseThrow();
    }

    private Long checkedChart(Long id) {
        if (id == null || id == 0L) return null;
        if (sizeCharts.findLive(id).isEmpty()) throw new InvalidRequestException("That size chart no longer exists. Choose another.");
        return id;
    }

    private static String cleanName(String name) {
        String label = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (label.isEmpty()) throw new InvalidRequestException("Give the fit a name, such as Oversized.");
        if (label.length() > 60) throw new InvalidRequestException("A fit's name can be at most 60 letters.");
        return label;
    }

    /** OVERSIZED for "Oversized", made unique: the name a variant stores, which never changes. */
    private String uniqueCode(String label) {
        String base = Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "FIT";
        if (base.length() > 34) base = base.substring(0, 34);
        String code = base;
        for (int n = 2; fits.existsByCode(code); n++) code = base + "_" + n;
        return code;
    }

    /** "OV" for Oversized, "LF" for Loose Fit: the letters a SKU carries. */
    static String skuCodeFor(String label) {
        String[] words = Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", " ").trim().split("\\s+");
        String code = words.length >= 2 ? "" + words[0].charAt(0) + words[1].charAt(0)
                : words[0].substring(0, Math.min(2, words[0].length()));
        return code.isEmpty() ? "FT" : code;
    }
}
