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
        long using = sizeChartRepository.countProductsUsing(id) + sizeChartRepository.countCategoriesUsing(id);
        sizeChartRepository.clearFromProducts(id);
        sizeChartRepository.clearRegularFitFromProducts(id);
        sizeChartRepository.clearFromCategories(id);
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
                .productCount(chart.getId() == null ? 0 : sizeChartRepository.countProductsUsing(chart.getId()))
                .categoryCount(chart.getId() == null ? 0 : sizeChartRepository.countCategoriesUsing(chart.getId()))
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

    /** The chart Regular Fit shows on this product: the one named for it, else the product's chart. */
    @Transactional(readOnly = true)
    public Long effectiveRegularFitFor(Product product) {
        if (product == null) return null;
        Long own = liveOrNull(product.getRegularFitSizeChartId());
        return own != null ? own : effectiveFor(product);
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
