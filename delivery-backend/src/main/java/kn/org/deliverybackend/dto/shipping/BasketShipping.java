package kn.org.deliverybackend.dto.shipping;

import java.math.BigDecimal;

/**
 * What the things in a basket say about what they cost to deliver.
 *
 * <p>Every line names a charge: the one set on the product, or the one set on
 * its category, or — when neither is set — the price of the customer's area. The
 * order pays the highest of them, once: one delivery, one charge.
 *
 * @param highestCharge the largest charge set on any line, or null when no line sets one
 * @param usesAreaPrice true when at least one line has no charge of its own and
 *                      so falls back to the area's price
 */
public record BasketShipping(BigDecimal highestCharge, boolean usesAreaPrice) {

    /** A basket where nothing has its own charge: the area's price decides, as it always did. */
    public static BasketShipping areaPriceOnly() {
        return new BasketShipping(null, true);
    }

    /**
     * What this basket costs to deliver to an area priced at {@code areaPrice}.
     * Pure arithmetic — the shop's free-shipping rules are applied around it by
     * {@code ShippingService.quoteShipping}.
     */
    public BigDecimal chargeAgainst(BigDecimal areaPrice) {
        BigDecimal area = areaPrice == null ? BigDecimal.ZERO : areaPrice;
        if (highestCharge == null) {
            return area;
        }
        return usesAreaPrice ? highestCharge.max(area) : highestCharge;
    }

    /** True when something in the basket carries a charge of its own. */
    public boolean decidedByItems() {
        return highestCharge != null;
    }
}
