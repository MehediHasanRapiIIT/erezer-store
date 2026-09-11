package kn.org.deliverybackend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * The arithmetic of changing prices across a category: one product's new
 * price, sale price and size prices. Kept apart from the database so the rules
 * can be tested on their own (see CATEGORY-PRICE-PLAN.md).
 */
public final class CategoryPriceChange {

    /** What happens to each product's price. */
    public enum PriceMode { KEEP, SET, RAISE_AMOUNT, LOWER_AMOUNT, RAISE_PERCENT, LOWER_PERCENT }

    /** What happens to each product's sale discount. */
    public enum SaleMode { KEEP, SET, REMOVE }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private CategoryPriceChange() {}

    /** Why a change can't be run, in words for the page; null when it can. */
    public static String invalidReason(PriceMode priceMode, BigDecimal priceValue,
                                       SaleMode saleMode, BigDecimal salePercent) {
        if (priceMode == null || saleMode == null) {
            return "Choose what happens to the price and to the sale discount.";
        }
        if (priceMode == PriceMode.KEEP && saleMode == SaleMode.KEEP) {
            return "Nothing would change: choose a new price or a sale discount.";
        }
        if (priceMode != PriceMode.KEEP) {
            if (priceValue == null || priceValue.signum() <= 0) {
                return "Enter an amount greater than 0.";
            }
            if (priceMode == PriceMode.LOWER_PERCENT && priceValue.compareTo(HUNDRED) >= 0) {
                return "A price can't be lowered by 100% or more.";
            }
        }
        if (saleMode == SaleMode.SET
                && (salePercent == null || salePercent.signum() <= 0 || salePercent.compareTo(HUNDRED) >= 0)) {
            return "The sale discount must be more than 0% and less than 100%.";
        }
        return null;
    }

    /** The product's new price, rounded to whole taka; unchanged (not rounded) when the price is kept. */
    public static BigDecimal newPrice(BigDecimal old, PriceMode mode, BigDecimal value) {
        return switch (mode) {
            case KEEP -> old;
            case SET -> wholeTaka(value);
            case RAISE_AMOUNT -> wholeTaka(old.add(value));
            case LOWER_AMOUNT -> wholeTaka(old.subtract(value));
            case RAISE_PERCENT -> wholeTaka(old.multiply(HUNDRED.add(value)).divide(HUNDRED));
            case LOWER_PERCENT -> wholeTaka(old.multiply(HUNDRED.subtract(value)).divide(HUNDRED));
        };
    }

    /**
     * A size's own price after the change. It gets the same amount or
     * percentage as the product; when the product is set to an exact price,
     * the size keeps its gap from the product price (an XXL ৳100 above stays
     * ৳100 above).
     */
    public static BigDecimal newSizePrice(BigDecimal oldSize, BigDecimal oldProductPrice,
                                          BigDecimal newProductPrice, PriceMode mode, BigDecimal value) {
        if (mode == PriceMode.SET) {
            return wholeTaka(oldSize.add(newProductPrice.subtract(oldProductPrice)));
        }
        return newPrice(oldSize, mode, value);
    }

    /**
     * The stored sale price after the change. "No sale" is stored the way the
     * product form stores it: the full price (or nothing, if nothing was there).
     */
    public static BigDecimal newSalePrice(BigDecimal oldPrice, BigDecimal oldSale, BigDecimal newPrice,
                                          SaleMode mode, BigDecimal salePercent) {
        return switch (mode) {
            case REMOVE -> newPrice;
            case SET -> ProductPricing.salePrice(newPrice, salePercent);
            case KEEP -> {
                if (!onSale(oldPrice, oldSale)) {
                    yield oldSale == null ? null : newPrice;
                }
                if (ProductPricing.sameAmount(oldPrice, newPrice)) {
                    yield oldSale;
                }
                // The same share off, on the new price.
                yield newPrice.multiply(oldSale).divide(oldPrice, 2, RoundingMode.HALF_UP);
            }
        };
    }

    /** True when the stored sale price really is below the price. */
    public static boolean onSale(BigDecimal price, BigDecimal sale) {
        return price != null && sale != null && sale.signum() > 0 && sale.compareTo(price) < 0;
    }

    /** "price raised by 10%; sale kept", for the activity log. */
    public static String describe(PriceMode priceMode, BigDecimal priceValue,
                                  SaleMode saleMode, BigDecimal salePercent) {
        String price = switch (priceMode) {
            case KEEP -> "price kept";
            case SET -> "price set to " + taka(priceValue);
            case RAISE_AMOUNT -> "price raised by " + taka(priceValue);
            case LOWER_AMOUNT -> "price lowered by " + taka(priceValue);
            case RAISE_PERCENT -> "price raised by " + percent(priceValue);
            case LOWER_PERCENT -> "price lowered by " + percent(priceValue);
        };
        String sale = switch (saleMode) {
            case KEEP -> "sale kept";
            case SET -> "sale set to " + percent(salePercent) + " off";
            case REMOVE -> "sale removed";
        };
        return price + "; " + sale;
    }

    /** ৳1,540 or ৳1,386.50. */
    public static String taka(BigDecimal amount) {
        if (amount == null) return "—";
        return "৳" + new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)).format(amount);
    }

    private static String percent(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString() + "%";
    }

    static BigDecimal wholeTaka(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.UNNECESSARY);
    }
}
