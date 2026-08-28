package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.Discount;
import kn.org.deliverybackend.enumeration.DiscountScope;
import kn.org.deliverybackend.enumeration.DiscountType;
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
 *   <li>Gather active discounts that apply: GLOBAL always; CATEGORY when its
 *       {@code targetId} equals the line's category; PRODUCT when its
 *       {@code targetId} equals the line's product.</li>
 *   <li>Sort by {@code priority} descending (tie-break: larger value, then id),
 *       and take the highest-priority discount as the <em>anchor</em>.</li>
 *   <li>If the anchor is <b>not stackable</b>, only the anchor applies (exclusive).
 *       If the anchor <b>is stackable</b>, every other stackable discount is added
 *       on top; non-stackable discounts below the anchor are ignored.</li>
 *   <li>Applied discounts reduce the line balance sequentially so the total
 *       discount never exceeds the line subtotal.</li>
 * </ol>
 *
 * Centralised here so the checkout quote ({@code CheckoutQuoteServiceImpl}) and
 * order placement ({@code OrderServiceImpl}) compute identical numbers.
 */
@Component
@RequiredArgsConstructor
public class DiscountEngine {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final DiscountService discountService;

    /**
     * @return the discount amount (>= 0, <= lineSubtotal) for one order line.
     */
    public BigDecimal discountForLine(Long productId, Long categoryId, BigDecimal lineSubtotal) {
        if (lineSubtotal == null || lineSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

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
