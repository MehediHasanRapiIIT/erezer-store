package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.settings.StoreSettingsDTO;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.service.CategoryTree;
import kn.org.deliverybackend.service.StoreSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which ways to pay a basket may use. For each method (cash on delivery, bKash,
 * card) the shop-wide switch in Settings comes first: off there, off for
 * everything. Otherwise the product's own setting wins; failing that its
 * category's, then the category above it; failing all of those it is allowed.
 *
 * <p>A basket may use a method only when every product in it allows it. Checkout
 * shows just those, and placing an order checks again, so a request that skips
 * the page is refused too.
 */
@Service
@RequiredArgsConstructor
public class PaymentMethodRules {

    public static final List<String> METHODS = List.of("CASH", "BKASH", "CARD");

    private final StoreSettingsService storeSettingsService;
    private final CategoryRepository categoryRepository;

    /** What a basket may be paid with, and why not when nothing fits. */
    public record BasketPayment(List<String> allowed, String message) {
    }

    /** Methods this product itself (or its category) does not allow; the shop-wide switches are not counted. */
    @Transactional(readOnly = true)
    public List<String> blockedFor(Product product) {
        Map<String, Boolean> seen = new HashMap<>();
        return METHODS.stream().filter(m -> Boolean.FALSE.equals(forProduct(product, m, seen))).toList();
    }

    @Transactional(readOnly = true)
    public BasketPayment forBasket(Collection<Product> products) {
        StoreSettingsDTO settings = storeSettingsService.get();
        Map<String, Boolean> seen = new HashMap<>();
        List<String> shopOn = METHODS.stream().filter(m -> shopAllows(settings, m)).toList();
        // For each product, the shop's methods it allows.
        Map<Product, List<String>> perProduct = new LinkedHashMap<>();
        for (Product p : products) {
            if (p == null) continue;
            perProduct.put(p, shopOn.stream().filter(m -> !Boolean.FALSE.equals(forProduct(p, m, seen))).toList());
        }
        List<String> allowed = new ArrayList<>(shopOn);
        perProduct.values().forEach(allowed::retainAll);
        if (!allowed.isEmpty() || perProduct.isEmpty()) return new BasketPayment(allowed, null);

        List<String> parts = new ArrayList<>();
        perProduct.forEach((p, ways) -> {
            if (ways.size() < shopOn.size()) parts.add(p.getName() + ": " + (ways.isEmpty() ? "can't be bought online right now"
                    : String.join(" or ", ways.stream().map(PaymentMethodRules::label).toList()) + " only"));
        });
        return new BasketPayment(allowed, "These items can't be paid for together ("
                + String.join("; ", parts) + "). Please order them separately.");
    }

    /** Refuses an order whose basket may not be paid with {@code method}. */
    @Transactional(readOnly = true)
    public void check(String method, Collection<Product> products) {
        BasketPayment basket = forBasket(products);
        if (basket.allowed().contains(method)) return;
        if (basket.message() != null) throw new InvalidRequestException(basket.message());
        Map<String, Boolean> seen = new HashMap<>();
        List<String> blocking = products.stream()
                .filter(p -> p != null && Boolean.FALSE.equals(forProduct(p, method, seen)))
                .map(Product::getName).distinct().toList();
        throw new InvalidRequestException(blocking.isEmpty()
                ? "This payment method isn't available right now. Please choose another one."
                : String.join(", ", blocking) + " can't be paid by "
                        + label(method) + ". Please choose another way to pay.");
    }

    /** The product's own switch, else its category's (nearest up the tree), else null. */
    private Boolean forProduct(Product product, String method, Map<String, Boolean> seen) {
        Boolean own = switch (method) {
            case "CASH" -> product.getPayCod();
            case "BKASH" -> product.getPayBkash();
            case "CARD" -> product.getPayCard();
            default -> null;
        };
        if (own != null || product.getCategoryId() == null) return own;
        String key = product.getCategoryId() + ":" + method;
        if (seen.containsKey(key)) return seen.get(key);
        Boolean fromCategory = categoryRepository.findById(product.getCategoryId())
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .map(c -> CategoryTree.payment(categoryRepository, c, method))
                .orElse(null);
        seen.put(key, fromCategory);
        return fromCategory;
    }

    static boolean shopAllows(StoreSettingsDTO s, String method) {
        return switch (method) {
            case "CASH" -> !Boolean.FALSE.equals(s.getPaymentCodEnabled());
            case "BKASH" -> !Boolean.FALSE.equals(s.getPaymentBkashEnabled());
            case "CARD" -> !Boolean.FALSE.equals(s.getPaymentCardEnabled());
            default -> false;
        };
    }

    public static String label(String method) {
        return switch (method) {
            case "CASH" -> "Cash on delivery";
            case "BKASH" -> "bKash";
            case "CARD" -> "Card";
            default -> method;
        };
    }

    /** Sets one category's or product's switch from an admin choice: ALLOW, BLOCK, FOLLOW; null leaves it. */
    static Boolean choice(String value, Boolean current) {
        if (value == null || value.isBlank()) return current;
        return switch (value.trim().toUpperCase()) {
            case "ALLOW" -> Boolean.TRUE;
            case "BLOCK" -> Boolean.FALSE;
            case "FOLLOW" -> null;
            default -> throw new InvalidRequestException("Choose Allowed, Not allowed or Same as category.");
        };
    }

    /** Applies a change to a category, for {@link #choice}. */
    static void apply(Category c, String cod, String bkash, String card) {
        c.setPayCod(choice(cod, c.getPayCod()));
        c.setPayBkash(choice(bkash, c.getPayBkash()));
        c.setPayCard(choice(card, c.getPayCard()));
    }

    static void apply(Product p, String cod, String bkash, String card) {
        p.setPayCod(choice(cod, p.getPayCod()));
        p.setPayBkash(choice(bkash, p.getPayBkash()));
        p.setPayCard(choice(card, p.getPayCard()));
    }
}
