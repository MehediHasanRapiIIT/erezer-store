package kn.org.deliverybackend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kn.org.deliverybackend.dto.settings.SizeChartDTO;
import kn.org.deliverybackend.dto.settings.SizeChartLibraryDTO;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.SizeChart;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.SizeChartRepository;
import kn.org.deliverybackend.service.CategoryTree;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * The shop's size charts, and which one a product shows.
 *
 * <p>A product shows the first of these that exists: the chart it names itself,
 * the chart named by its category or by the nearest category above that names
 * one, the default chart. A product that comes in two fits may name a second
 * chart for Regular Fit; without one, Regular Fit shows the same chart.
 */
@Service
@RequiredArgsConstructor
public class SizeChartLibraryService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SizeChartRepository sizeChartRepository;
    private final CategoryRepository categoryRepository;
    private final kn.org.deliverybackend.repository.ProductFitSizeChartRepository productFitCharts;
    private final kn.org.deliverybackend.repository.FitTypeRepository fitTypes;
    private final kn.org.deliverybackend.repository.CategoryFitSizeChartRepository categoryFitCharts;

    // ── the library ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SizeChartLibraryDTO> list() {
        return sizeChartRepository.findLive().stream().map(this::toDTO).toList();
    }

    @Transactional
    public SizeChartLibraryDTO create(SizeChartLibraryDTO request) {
        SizeChart chart = new SizeChart();
        chart.setIsDefault(false);
        apply(chart, request);
        // The first chart of a library is the default: there is nothing else to fall back on.
        if (sizeChartRepository.findDefaults().isEmpty()) chart.setIsDefault(true);
        SizeChart saved = sizeChartRepository.save(chart);
        if (Boolean.TRUE.equals(request.getIsDefault())) makeDefault(saved);
        return toDTO(saved);
    }

    @Transactional
    public SizeChartLibraryDTO update(Long id, SizeChartLibraryDTO request) {
        SizeChart chart = live(id);
        apply(chart, request);
        SizeChart saved = sizeChartRepository.save(chart);
        if (Boolean.TRUE.equals(request.getIsDefault())) makeDefault(saved);
        return toDTO(saved);
    }

    /** Makes this the chart shown when nothing else is named; the one that was, no longer is. */
    @Transactional
    public SizeChartLibraryDTO setDefault(Long id) {
        SizeChart chart = live(id);
        makeDefault(chart);
        return toDTO(chart);
    }

    /**
     * Deletes a chart. Products and categories that named it let go of it, so
     * they show their category's chart or the default instead. Returns how many
     * did.
     */
    @Transactional
    public long delete(Long id) {
        SizeChart chart = live(id);
        long using = sizeChartRepository.countProductsUsing(id) + productFitCharts.countProductsUsing(id)
                + sizeChartRepository.countCategoriesUsing(id) + categoryFitCharts.countCategoriesUsing(id) + fitTypes.countUsingChart(id);
        sizeChartRepository.clearFromProducts(id);
        productFitCharts.clearChart(id);
        categoryFitCharts.clearChart(id);
        sizeChartRepository.clearFromCategories(id);
        fitTypes.clearChart(id);
        chart.setDeleted(true);
        chart.setDeletedAt(new java.util.Date());
        boolean wasDefault = Boolean.TRUE.equals(chart.getIsDefault());
        chart.setIsDefault(false);
        sizeChartRepository.save(chart);
        // The shop always has a default while it has any chart at all.
        if (wasDefault) {
            sizeChartRepository.findLive().stream().filter(c -> !c.getId().equals(id)).findFirst()
                    .ifPresent(this::makeDefault);
        }
        return using;
    }

    private void makeDefault(SizeChart chart) {
        for (SizeChart other : sizeChartRepository.findDefaults()) {
            if (!other.getId().equals(chart.getId())) {
                other.setIsDefault(false);
                sizeChartRepository.save(other);
            }
        }
        chart.setIsDefault(true);
        sizeChartRepository.save(chart);
    }

    private void apply(SizeChart chart, SizeChartLibraryDTO request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) throw new InvalidRequestException("Give the chart a name.");
        SizeChartDTO body = request.getChart();
        if (body == null || body.getColumns() == null || body.getColumns().isEmpty()) {
            throw new InvalidRequestException("A chart needs at least one column, such as Chest.");
        }
        if (body.getRows() == null) body.setRows(new ArrayList<>());
        chart.setName(name);
        try {
            chart.setChartJson(JSON.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("That chart could not be saved.");
        }
    }

    private SizeChart live(Long id) {
        return sizeChartRepository.findLive(id)
                .orElseThrow(() -> new ResourceNotFoundException("Size chart not found: " + id));
    }

    private SizeChartLibraryDTO toDTO(SizeChart chart) {
        return SizeChartLibraryDTO.builder()
                .id(chart.getId())
                .name(chart.getName())
                .chart(read(chart.getChartJson()))
                .isDefault(Boolean.TRUE.equals(chart.getIsDefault()))
                .productCount(chart.getId() == null ? 0
                        : sizeChartRepository.countProductsUsing(chart.getId()) + productFitCharts.countProductsUsing(chart.getId()))
                .fitCount(chart.getId() == null ? 0 : fitTypes.countUsingChart(chart.getId()))
                .categoryCount(chart.getId() == null ? 0
                        : sizeChartRepository.countCategoriesUsing(chart.getId()) + categoryFitCharts.countCategoriesUsing(chart.getId()))
                .build();
    }

    private static SizeChartDTO read(String json) {
        try {
            return json == null || json.isBlank() ? new SizeChartDTO(new ArrayList<>(), new ArrayList<>())
                    : JSON.readValue(json, SizeChartDTO.class);
        } catch (JsonProcessingException e) {
            return new SizeChartDTO(new ArrayList<>(), new ArrayList<>());
        }
    }

    // ── naming a chart on a product or a category ────────────────────────────

    /**
     * A chart id as a form sends it, made ready to store: null and 0 both mean
     * "none of its own", and anything else has to be a chart that exists.
     */
    public Long checked(Long requested) {
        if (requested == null || requested == 0L) return null;
        if (sizeChartRepository.findLive(requested).isEmpty()) {
            throw new InvalidRequestException("That size chart no longer exists. Choose another.");
        }
        return requested;
    }

    // ── which chart a product shows ──────────────────────────────────────────

    /** The chart a product shows: its own, else its category's or one above, else the default. Null when the library is empty. */
    @Transactional(readOnly = true)
    public Long effectiveFor(Product product) {
        if (product == null) return null;
        Long own = liveOrNull(product.getSizeChartId());
        if (own != null) return own;
        if (product.getCategoryId() != null) {
            Category category = categoryRepository.findById(product.getCategoryId())
                    .filter(c -> !Boolean.TRUE.equals(c.getDeleted())).orElse(null);
            Long fromCategory = forCategory(category);
            if (fromCategory != null) return fromCategory;
        }
        return defaultId();
    }

    /** The chart Regular Fit shows on this product. */
    @Transactional(readOnly = true)
    public Long effectiveRegularFitFor(Product product) {
        return effectiveForFit(product, "REGULAR_FIT");
    }

    /**
     * The chart a fit's sizes show on this product: the product's own chart for
     * that fit, else its category's chart for that fit (or that of the nearest
     * category above with one), else the fit's chart (from the Fits list), else
     * the product's chart.
     */
    @Transactional(readOnly = true)
    public Long effectiveForFit(Product product, String fitCode) {
        if (product == null) return null;
        if (product.getId() != null && fitCode != null) {
            for (kn.org.deliverybackend.entity.ProductFitSizeChart own : productFitCharts.findByProductId(product.getId())) {
                if (fitCode.equals(own.getFitCode())) {
                    Long live = liveOrNull(own.getSizeChartId());
                    if (live != null) return live;
                }
            }
        }
        if (fitCode != null && product.getCategoryId() != null) {
            Category category = categoryRepository.findById(product.getCategoryId())
                    .filter(c -> !Boolean.TRUE.equals(c.getDeleted())).orElse(null);
            Long fromCategory = forCategoryFit(category, fitCode);
            if (fromCategory != null) return fromCategory;
        }
        if (fitCode != null) {
            for (kn.org.deliverybackend.enumeration.Fit fit : kn.org.deliverybackend.enumeration.Fit.values()) {
                if (fit.name().equals(fitCode)) {
                    Long fitsOwn = liveOrNull(fit.sizeChartId());
                    if (fitsOwn != null) return fitsOwn;
                }
            }
        }
        return effectiveFor(product);
    }

    /** The chart every fit shows on this product, by fit, in the shop's order of fits. */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> effectiveFitCharts(Product product) {
        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        for (kn.org.deliverybackend.enumeration.Fit fit : kn.org.deliverybackend.enumeration.Fit.values()) {
            out.put(fit.name(), effectiveForFit(product, fit.name()));
        }
        return out;
    }

    /** The product's own chart for each fit that has one, by fit. */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> ownFitCharts(Long productId) {
        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        if (productId == null) return out;
        for (kn.org.deliverybackend.entity.ProductFitSizeChart own : productFitCharts.findByProductId(productId)) {
            if (liveOrNull(own.getSizeChartId()) != null) out.put(own.getFitCode(), own.getSizeChartId());
        }
        return out;
    }

    /**
     * Saves a product's own charts for its fits: a chart id gives a fit its own
     * chart, 0 takes it away. Fits not in the map are left as they are.
     */
    @Transactional
    public void setOwnFitCharts(Long productId, java.util.Map<String, Long> charts) {
        if (productId == null || charts == null) return;
        for (java.util.Map.Entry<String, Long> entry : charts.entrySet()) {
            kn.org.deliverybackend.enumeration.Fit fit = kn.org.deliverybackend.enumeration.Fit.parse(entry.getKey());
            if (fit == null) continue;
            Long chart = checked(entry.getValue());
            if (chart == null) {
                productFitCharts.deleteOne(productId, fit.name());
            } else {
                productFitCharts.save(new kn.org.deliverybackend.entity.ProductFitSizeChart(productId, fit.name(), chart));
            }
        }
    }

    /** A category's chart for a fit: its own for it, else that of the nearest category above with one. Null for none. */
    @Transactional(readOnly = true)
    public Long forCategoryFit(Category category, String fitCode) {
        if (category == null || fitCode == null) return null;
        Long own = ownForCategoryFit(category.getId(), fitCode);
        if (own != null) return own;
        for (Category above : CategoryTree.ancestors(categoryRepository, category)) {
            Long theirs = ownForCategoryFit(above.getId(), fitCode);
            if (theirs != null) return theirs;
        }
        return null;
    }

    private Long ownForCategoryFit(Long categoryId, String fitCode) {
        if (categoryId == null) return null;
        for (kn.org.deliverybackend.entity.CategoryFitSizeChart own : categoryFitCharts.findByCategoryId(categoryId)) {
            if (fitCode.equals(own.getFitCode())) return liveOrNull(own.getSizeChartId());
        }
        return null;
    }

    /** The category's own chart for each fit that has one, by fit. */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> ownCategoryFitCharts(Long categoryId) {
        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        if (categoryId == null) return out;
        for (kn.org.deliverybackend.entity.CategoryFitSizeChart own : categoryFitCharts.findByCategoryId(categoryId)) {
            if (liveOrNull(own.getSizeChartId()) != null) out.put(own.getFitCode(), own.getSizeChartId());
        }
        return out;
    }

    /** Saves a category's charts for the fits: a chart id sets one, 0 takes it away; fits left out are left as they are. */
    @Transactional
    public void setCategoryFitCharts(Long categoryId, java.util.Map<String, Long> charts) {
        if (categoryId == null || charts == null) return;
        for (java.util.Map.Entry<String, Long> entry : charts.entrySet()) {
            kn.org.deliverybackend.enumeration.Fit fit = kn.org.deliverybackend.enumeration.Fit.parse(entry.getKey());
            if (fit == null) continue;
            Long chart = checked(entry.getValue());
            if (chart == null) {
                categoryFitCharts.deleteOne(categoryId, fit.name());
            } else {
                categoryFitCharts.save(new kn.org.deliverybackend.entity.CategoryFitSizeChart(categoryId, fit.name(), chart));
            }
        }
    }

    /** The chart a category gives its products: its own, else that of the nearest category above that names one. Null for none. */
    @Transactional(readOnly = true)
    public Long forCategory(Category category) {
        if (category == null) return null;
        Long own = liveOrNull(category.getSizeChartId());
        if (own != null) return own;
        for (Category above : CategoryTree.ancestors(categoryRepository, category)) {
            Long theirs = liveOrNull(above.getSizeChartId());
            if (theirs != null) return theirs;
        }
        return null;
    }

    private Long defaultId() {
        return sizeChartRepository.findDefaults().stream().findFirst().map(SizeChart::getId).orElse(null);
    }

    private Long liveOrNull(Long id) {
        return id == null ? null : sizeChartRepository.findLive(id).map(SizeChart::getId).orElse(null);
    }
}
