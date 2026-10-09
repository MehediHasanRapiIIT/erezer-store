package kn.org.deliverybackend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kn.org.deliverybackend.dto.bundle.BundleTierDTO;
import kn.org.deliverybackend.entity.BundleOffer;
import kn.org.deliverybackend.enumeration.BundleType;
import kn.org.deliverybackend.exception.InvalidRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What each kind of bundle offer asks for and what it takes off.
 *
 * <p>Kept apart from the service so the rules can be read, and tested, without
 * a database: how many items an offer accepts, the discount for a basket of a
 * given size and value, the sentence that describes the offer, and the checks
 * an offer must pass before it is saved.
 */
public final class BundlePricing {

    /** The most items one quantity-discount order takes. */
    public static final int MAX_ITEMS = 20;
    /** The most steps a quantity discount has. */
    public static final int MAX_TIERS = 6;

    private static final ObjectMapper JSON = new ObjectMapper();

    private BundlePricing() {}

    public static BundleType typeOf(BundleOffer b) {
        return BundleType.of(b.getOfferType(), b.getGetCount());
    }

    /** The steps of a quantity discount, smallest quantity first. Empty for the other kinds. */
    public static List<BundleTierDTO> tiersOf(BundleOffer b) {
        String json = b.getTiersJson();
        if (json == null || json.isBlank()) return List.of();
        try {
            List<BundleTierDTO> tiers = new ArrayList<>(JSON.readValue(json, new TypeReference<List<BundleTierDTO>>() {}));
            tiers.removeIf(t -> t == null || t.getQuantity() == null || t.getPercentOff() == null);
            tiers.sort(Comparator.comparing(BundleTierDTO::getQuantity));
            return tiers;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    /** The fewest items the customer picks. */
    public static int minItems(BundleOffer b) {
        if (typeOf(b) == BundleType.QUANTITY_DISCOUNT) {
            List<BundleTierDTO> tiers = tiersOf(b);
            return tiers.isEmpty() ? 1 : tiers.get(0).getQuantity();
        }
        return b.slots();
    }

    /** The most items the customer picks: the same as the fewest, except for a quantity discount. */
    public static int maxItems(BundleOffer b) {
        return typeOf(b) == BundleType.QUANTITY_DISCOUNT ? Math.max(MAX_ITEMS, minItems(b)) : b.slots();
    }

    /** The percentage a basket of this many items earns: that of the highest step reached. Zero below the first step. */
    public static BigDecimal percentFor(BundleOffer b, int quantity) {
        BigDecimal percent = BigDecimal.ZERO;
        for (BundleTierDTO tier : tiersOf(b)) {
            if (quantity >= tier.getQuantity()) percent = tier.getPercentOff();
        }
        return percent;
    }

    /**
     * What comes off a basket of {@code quantity} items worth {@code subtotal}.
     * For a fixed price or Buy X Get Y it is whatever brings the total down to
     * the bundle price; for a quantity discount, the step's percentage.
     */
    public static BigDecimal discount(BundleOffer b, int quantity, BigDecimal subtotal) {
        BigDecimal goods = subtotal == null ? BigDecimal.ZERO : subtotal;
        BigDecimal off = typeOf(b) == BundleType.QUANTITY_DISCOUNT
                ? goods.multiply(percentFor(b, quantity)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : goods.subtract(b.getBundlePrice() == null ? BigDecimal.ZERO : b.getBundlePrice());
        return off.signum() < 0 ? BigDecimal.ZERO : off;
    }

    /** "Any 3 for ৳999", "Buy 2 Get 1 Free", "Buy 2, save 10% · Buy 3, save 15%". */
    public static String headline(BundleOffer b) {
        return switch (typeOf(b)) {
            case FIXED_PRICE -> "Any " + b.slots() + " for ৳" + plain(b.getBundlePrice());
            case BUY_X_GET_Y -> "Buy " + b.getBuyCount() + " Get " + b.getGetCount() + " Free";
            case QUANTITY_DISCOUNT -> {
                List<String> steps = tiersOf(b).stream()
                        .map(t -> "Buy " + t.getQuantity() + ", save " + plain(t.getPercentOff()) + "%").toList();
                yield steps.isEmpty() ? "Buy more, save more" : String.join(" · ", steps);
            }
        };
    }

    /**
     * The steps as they may be stored: each a quantity of at least 2 and a
     * percentage above 0 and below 100, no quantity twice, and a bigger
     * quantity never earning less than a smaller one.
     */
    public static List<BundleTierDTO> checkedTiers(List<BundleTierDTO> requested) {
        List<BundleTierDTO> tiers = new ArrayList<>();
        Set<Integer> quantities = new HashSet<>();
        for (BundleTierDTO tier : requested == null ? List.<BundleTierDTO>of() : requested) {
            if (tier == null || (tier.getQuantity() == null && tier.getPercentOff() == null)) continue;
            if (tier.getQuantity() == null || tier.getQuantity() < 2) {
                throw new InvalidRequestException("Each step needs a quantity of 2 or more.");
            }
            if (tier.getQuantity() > MAX_ITEMS) {
                throw new InvalidRequestException("A step can be for at most " + MAX_ITEMS + " items.");
            }
            BigDecimal percent = tier.getPercentOff();
            if (percent == null || percent.signum() <= 0 || percent.compareTo(BigDecimal.valueOf(100)) >= 0) {
                throw new InvalidRequestException("Each step needs a percentage above 0 and below 100.");
            }
            if (!quantities.add(tier.getQuantity())) {
                throw new InvalidRequestException("Two steps are for " + tier.getQuantity() + " items. Keep one.");
            }
            tiers.add(new BundleTierDTO(tier.getQuantity(), percent.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()));
        }
        if (tiers.isEmpty()) throw new InvalidRequestException("Add at least one step, such as 2 items for 10% off.");
        if (tiers.size() > MAX_TIERS) throw new InvalidRequestException("Up to " + MAX_TIERS + " steps.");
        tiers.sort(Comparator.comparing(BundleTierDTO::getQuantity));
        for (int i = 1; i < tiers.size(); i++) {
            if (tiers.get(i).getPercentOff().compareTo(tiers.get(i - 1).getPercentOff()) <= 0) {
                throw new InvalidRequestException("Buying " + tiers.get(i).getQuantity() + " must save more than buying "
                        + tiers.get(i - 1).getQuantity() + ".");
            }
        }
        return tiers;
    }

    public static String write(List<BundleTierDTO> tiers) {
        try {
            return JSON.writeValueAsString(tiers);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("Those steps could not be saved.");
        }
    }

    /** 999 for 999.00, 12.5 for 12.50. */
    private static String plain(BigDecimal number) {
        if (number == null) return "0";
        BigDecimal stripped = number.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }
}
