package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.product.PriceChangeRequestDTO;
import kn.org.deliverybackend.dto.response.product.PriceChangePreviewDTO;
import kn.org.deliverybackend.dto.response.product.PriceChangePreviewDTO.Row;
import kn.org.deliverybackend.dto.response.product.PriceChangePreviewDTO.SizeRow;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import kn.org.deliverybackend.service.CategoryPriceChange;
import kn.org.deliverybackend.service.PriceChangeService;
import kn.org.deliverybackend.service.ProductPricing;
import kn.org.deliverybackend.util.SearchText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PriceChangeServiceImpl implements PriceChangeService {

    private final ProductRepository productRepository;
    private final VariantRepository variantRepository;
    private final CategoryRepository categoryRepository;

    /** One product's change, worked out but not yet saved. */
    private record Planned(Product product, BigDecimal price, BigDecimal sale,
                           List<SizePlan> sizes, Row row) {
    }

    private record SizePlan(Variant variant, BigDecimal price) {
    }

    @Override
    @Transactional(readOnly = true)
    public PriceChangePreviewDTO preview(PriceChangeRequestDTO request, String q, int page, int size) {
        Category category = checked(request);
        // Worked out for the whole category (a few hundred products at most), so the
        // counts and the changeable products cover every page; only one page is sent.
        List<Planned> plan = plan(productRepository.findLiveByCategory(category.getId()), request);
        String text = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Row> matching = plan.stream()
                .map(Planned::row)
                .filter(r -> text.isEmpty() || contains(r.name(), text) || contains(r.sku(), text)
                        || contains(r.productCode(), text))
                .toList();
        int pageSize = SearchText.pageSize(size);
        int totalPages = Math.max(1, (matching.size() + pageSize - 1) / pageSize);
        // A page past the end (after a search narrowed the list) shows the last one.
        int current = Math.min(Math.max(page, 0), totalPages - 1);
        List<Row> rows = matching.subList(Math.min(current * pageSize, matching.size()),
                Math.min((current + 1) * pageSize, matching.size()));
        return result(category, plan, current, pageSize, matching.size(), totalPages, rows);
    }

    @Override
    @Transactional
    public PriceChangePreviewDTO apply(PriceChangeRequestDTO request) {
        Category category = checked(request);
        if (request.productIds() == null || request.productIds().isEmpty()) {
            throw new InvalidRequestException("Tick at least one product to change.");
        }
        Set<Long> ticked = new LinkedHashSet<>(request.productIds());
        List<Product> products = productRepository.findLiveByCategory(category.getId()).stream()
                .filter(p -> ticked.contains(p.getId()))
                .toList();
        if (products.size() != ticked.size()) {
            throw new InvalidRequestException("Some ticked products are no longer in " + category.getName()
                    + ". Nothing was changed; show the preview again.");
        }

        List<Planned> plan = plan(products, request);
        List<String> problems = plan.stream()
                .filter(p -> p.row().problem() != null)
                .map(p -> p.row().name() + ": " + p.row().problem())
                .toList();
        if (!problems.isEmpty()) {
            throw new InvalidRequestException("Nothing was changed. " + String.join(" ", problems));
        }

        List<Variant> sizes = new ArrayList<>();
        for (Planned p : plan) {
            p.product().setPrice(p.price());
            p.product().setDiscountPrice(p.sale());
            for (SizePlan s : p.sizes()) {
                s.variant().setPriceOverride(s.price());
                sizes.add(s.variant());
            }
        }
        productRepository.saveAll(products);
        variantRepository.saveAll(sizes);
        List<Row> all = plan.stream().map(Planned::row).toList();
        return result(category, plan, 0, all.size(), all.size(), 1, all);
    }

    private Category checked(PriceChangeRequestDTO request) {
        String invalid = CategoryPriceChange.invalidReason(
                request.priceMode(), request.priceValue(), request.saleMode(), request.salePercent());
        if (invalid != null) {
            throw new InvalidRequestException(invalid);
        }
        return categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.categoryId()));
    }

    private List<Planned> plan(List<Product> products, PriceChangeRequestDTO request) {
        Map<Long, List<Variant>> sizesByProduct = products.isEmpty() ? Map.of()
                : variantRepository.findLiveByProductIds(products.stream().map(Product::getId).toList()).stream()
                        .filter(v -> v.getPriceOverride() != null)
                        .collect(Collectors.groupingBy(Variant::getProductId));
        return products.stream()
                .map(p -> planOne(p, sizesByProduct.getOrDefault(p.getId(), List.of()), request))
                .toList();
    }

    private Planned planOne(Product product, List<Variant> sizes, PriceChangeRequestDTO r) {
        BigDecimal oldPrice = product.getPrice();
        BigDecimal oldSale = product.getDiscountPrice();
        if (oldPrice == null) {
            Row row = new Row(product.getId(), product.getName(), product.getSku(), product.getProductCode(),
                    product.getImageUrl(), null, null, null, null, List.of(), false, "This product has no price yet.");
            return new Planned(product, null, oldSale, List.of(), row);
        }

        BigDecimal price = CategoryPriceChange.newPrice(oldPrice, r.priceMode(), r.priceValue());
        BigDecimal sale = CategoryPriceChange.newSalePrice(oldPrice, oldSale, price, r.saleMode(), r.salePercent());

        List<SizePlan> sizePlans = new ArrayList<>();
        List<SizeRow> sizeRows = new ArrayList<>();
        boolean changed = !ProductPricing.sameAmount(price, oldPrice) || !ProductPricing.sameAmount(sale, oldSale);
        boolean sizeTooLow = false;
        for (Variant v : sizes) {
            BigDecimal newSize = CategoryPriceChange.newSizePrice(
                    v.getPriceOverride(), oldPrice, price, r.priceMode(), r.priceValue());
            sizeRows.add(new SizeRow(v.getId(), v.getSize(), v.getPriceOverride(), newSize));
            sizePlans.add(new SizePlan(v, newSize));
            changed |= !ProductPricing.sameAmount(newSize, v.getPriceOverride());
            sizeTooLow |= newSize.signum() <= 0;
        }

        String problem = price.signum() <= 0 ? "The new price would be ৳0 or less."
                : sizeTooLow ? "A size's new price would be ৳0 or less." : null;
        Row row = new Row(product.getId(), product.getName(), product.getSku(), product.getProductCode(),
                product.getImageUrl(), oldPrice, price,
                CategoryPriceChange.onSale(oldPrice, oldSale) ? oldSale : null,
                CategoryPriceChange.onSale(price, sale) ? sale : null,
                sizeRows, changed, problem);
        return new Planned(product, price, sale, sizePlans, row);
    }

    /** Counts over the whole plan; {@code rows} is the page being sent. */
    private static PriceChangePreviewDTO result(Category category, List<Planned> plan, int page, int size,
                                                int totalRows, int totalPages, List<Row> rows) {
        List<Row> all = plan.stream().map(Planned::row).toList();
        int changed = (int) all.stream().filter(Row::changed).count();
        int problems = (int) all.stream().filter(row -> row.problem() != null).count();
        List<Long> changeable = all.stream()
                .filter(row -> row.changed() && row.problem() == null)
                .map(Row::productId)
                .toList();
        return new PriceChangePreviewDTO(category.getId(), category.getName(), all.size(), changed, problems,
                changeable, page, size, totalRows, totalPages, rows);
    }

    private static boolean contains(String value, String lowerText) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerText);
    }
}
