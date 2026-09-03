package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.Discount;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.enumeration.DiscountScope;
import kn.org.deliverybackend.enumeration.DiscountType;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.service.DiscountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Applies automatic {@link Discount}s to an order line.
 *
 * <p>Resolution rules (per line):
 * <ol>
 *   <li>If the product, or the category it belongs to, is excluded from
 *       automatic discounts, the line is left at full price. An exclusion beats
 *       every rule, including a store-wide one.</li>
 *   <li>Gather active discounts that apply: GLOBAL always; CATEGORY when its
 *       {@code targetId} equals the line's category; PRODUCT when its
 *       {@code targetId} equals the line's product. The switches an admin can
 *       flip are applied upstream, in {@code DiscountService.activeDiscounts()},
 *       so a suspended rule never reaches this method.</li>
 *   <li>Sort by {@code priority} descending (tie-break: larger value, then id),
 *       and take the highest-priority discount as the <em>anchor</em>.</li>
 *   <li>If the anchor is <b>not stackable</b>, only the anchor applies (exclusive).
 *       If the anchor <b>is stackable</b>, every other stackable discount is added
 *       on top; non-stackable discounts below the anchor are ignored.</li>
 *   <li>Applied discounts reduce the line balance sequentially so the total
 *       discount never exceeds the line subtotal.</li>
 * </ol>
 *
 * <p>Note that a product's own sale price is not a discount in this sense: it is
 * set on the product row and folded into the unit price by {@code PricingSupport}
 * before this method is reached. Excluding a product here keeps its sale price.
 *
 * <p>Centralised here so the checkout quote ({@code CheckoutQuoteServiceImpl}) and
 * order placement ({@code OrderServiceImpl}) compute identical numbers.
 */
@Component
@RequiredArgsConstructor
public class DiscountEngine {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final DiscountService discountService;
    private final CategoryRepository categoryRepository;

    /**
     * @param product      the line's product; the whole entity rather than its id
     *                     so the exclusion flag cannot be forgotten by a caller
     * @param lineSubtotal quantity × unit price for the line
     * @return the discount amount (>= 0, <= lineSubtotal) for one order line.
     */
    public BigDecimal discountForLine(Product product, BigDecimal lineSubtotal) {
        if (product == null || lineSubtotal == null || lineSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (isExcluded(product)) {
            return BigDecimal.ZERO;
        }

        Long productId = product.getId();
        Long categoryId = product.getCategoryId();

        List<Discount> candidates = new ArrayList<>();
        for (Discount d : discountService.activeDiscounts()) {
            if (applies(d, productId, categoryId)) {
                candidates.add(d);
            }
        }
        if (candidates.isEmpty()) {
            return BigDecimal.ZERO;
        }

        candidates.sort(
                Comparator.comparing((Discount d) -> d.getPriority() == null ? 0 : d.getPriority())
                        .reversed()
                        .thenComparing(d -> d.getDiscountValue() == null ? BigDecimal.ZERO : d.getDiscountValue(),
                                Comparator.reverseOrder())
                        .thenComparing(d -> d.getId().toString()));

        Discount anchor = candidates.get(0);
        List<Discount> applied = new ArrayList<>();
        applied.add(anchor);
        if (Boolean.TRUE.equals(anchor.getStackable())) {
            for (int i = 1; i < candidates.size(); i++) {
                Discount d = candidates.get(i);
                if (Boolean.TRUE.equals(d.getStackable())) {
                    applied.add(d);
                }
            }
        }

        BigDecimal remaining = lineSubtotal;
        for (Discount d : applied) {
            BigDecimal cut = computeCut(d, remaining);
            remaining = remaining.subtract(cut);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                remaining = BigDecimal.ZERO;
                break;
            }
        }
        // Total discount = original − remaining, capped at the line subtotal.
        BigDecimal discount = lineSubtotal.subtract(remaining);
        return discount.max(BigDecimal.ZERO).min(lineSubtotal).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Whether the admin has kept this line at full price, either by excluding
     * the product itself or the whole category it sits in.
     */
    private boolean isExcluded(Product product) {
        if (Boolean.TRUE.equals(product.getDiscountExcluded())) {
            return true;
        }
        Long categoryId = product.getCategoryId();
        if (categoryId == null) {
            return false;
        }
        // One lookup per line. Within a checkout transaction Hibernate serves
        // repeats of the same category from its first-level cache.
        return categoryRepository.findById(categoryId)
                .map(c -> Boolean.TRUE.equals(c.getDiscountExcluded()))
                .orElse(false);
    }

    private boolean applies(Discount d, Long productId, Long categoryId) {
        DiscountScope scope = DiscountScope.parse(d.getScope()).orElse(null);
        if (scope == null) return false;
        return switch (scope) {
            case GLOBAL -> true;
            case CATEGORY -> d.getTargetId() != null && d.getTargetId().equals(categoryId);
            case PRODUCT -> d.getTargetId() != null && d.getTargetId().equals(productId);
        };
    }

    /** Discount amount for one discount against the current (reducing) balance. */
    private BigDecimal computeCut(Discount d, BigDecimal balance) {
        DiscountType type = DiscountType.parse(d.getDiscountType()).orElse(null);
        if (type == null) return BigDecimal.ZERO;
        BigDecimal value = d.getDiscountValue() == null ? BigDecimal.ZERO : d.getDiscountValue();
        return switch (type) {
            case PERCENT -> balance.multiply(value)
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP)
                    .min(balance);
            case FLAT -> value.min(balance);
        };
    }
}
