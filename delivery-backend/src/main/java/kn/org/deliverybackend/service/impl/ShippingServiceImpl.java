package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.shipping.BasketShipping;
import kn.org.deliverybackend.dto.shipping.BulkShippingChargeRequestDTO;
import kn.org.deliverybackend.dto.shipping.BulkShippingChargeResultDTO;
import kn.org.deliverybackend.dto.shipping.ShippingQuote;
import kn.org.deliverybackend.dto.shipping.ShippingRulesChangeDTO;
import kn.org.deliverybackend.dto.shipping.ShippingSettingsDTO;
import kn.org.deliverybackend.dto.shipping.ShippingZoneDTO;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.ShippingZone;
import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.entity.TaxRule;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.ShippingZoneRepository;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import kn.org.deliverybackend.repository.TaxRuleRepository;
import kn.org.deliverybackend.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShippingServiceImpl implements ShippingService {

    private final ShippingZoneRepository zoneRepository;
    private final TaxRuleRepository taxRepository;
    private final StoreSettingsRepository settingsRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ShippingZoneDTO> listActive() {
        return zoneRepository.findAllActive().stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ShippingZone resolveZone(String deliveryAddress) {
        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            return defaultZone();
        }
        String addr = deliveryAddress.toLowerCase();
        return zoneRepository.findAllActive().stream()
                .filter(z -> z.getRegionKeywords() != null && !z.getRegionKeywords().isBlank())
                .filter(z -> matchesAnyKeyword(addr, z.getRegionKeywords()))
                .findFirst()
                .orElseGet(this::defaultZone);
    }

    @Override
    @Transactional(readOnly = true)
    public ShippingZone defaultZone() {
        return zoneRepository.findDefault()
                .or(() -> zoneRepository.findAllActive().stream().findFirst())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No shipping zones configured. Create one in the admin panel."));
    }

    @Override
    @Transactional(readOnly = true)
    public ShippingQuote quoteShipping(ShippingZone zone, BigDecimal goodsTotal) {
        return quoteShipping(zone, goodsTotal, null);
    }

    @Override
    @Transactional(readOnly = true)
    public ShippingQuote quoteShipping(ShippingZone zone, BigDecimal goodsTotal, BasketShipping basket) {
        StoreSettings s = settingsRepository.findById(StoreSettings.SINGLETON_ID).orElse(null);
        boolean freeAll = s != null && Boolean.TRUE.equals(s.getShippingFreeAll());
        BigDecimal offerMin = s != null && Boolean.TRUE.equals(s.getShippingOfferEnabled())
                && s.getShippingOfferMin() != null && s.getShippingOfferMin().signum() > 0
                ? s.getShippingOfferMin() : null;

        // The shop's own rules come first: a switch an admin turned on beats any
        // charge set on a product.
        if (freeAll) {
            return new ShippingQuote(BigDecimal.ZERO, ShippingQuote.FREE_ALL, offerMin);
        }
        if (offerMin != null && goodsTotal != null && goodsTotal.compareTo(offerMin) >= 0) {
            return new ShippingQuote(BigDecimal.ZERO, ShippingQuote.OFFER, offerMin);
        }
        // Zones used to carry their own free-above threshold; it is no longer read
        // (V16), so the only ways to free shipping are the rules above, a
        // free-shipping coupon, and a basket set to be delivered free.
        BigDecimal areaPrice = zone != null && zone.getFlatFee() != null ? zone.getFlatFee() : BigDecimal.ZERO;
        if (basket == null) {
            return new ShippingQuote(areaPrice, null, offerMin);
        }
        BigDecimal fee = basket.chargeAgainst(areaPrice);
        String reason = fee.signum() == 0 && basket.decidedByItems() ? ShippingQuote.PRODUCT : null;
        return new ShippingQuote(fee, reason, offerMin);
    }

    @Override
    @Transactional(readOnly = true)
    public ShippingSettingsDTO settings() {
        StoreSettings s = settingsRepository.findById(StoreSettings.SINGLETON_ID).orElse(null);
        return new ShippingSettingsDTO(
                zoneRepository.findAllForAdmin().stream().map(this::toDTO).toList(),
                s != null && Boolean.TRUE.equals(s.getShippingFreeAll()),
                s != null && Boolean.TRUE.equals(s.getShippingOfferEnabled()),
                s == null ? null : s.getShippingOfferMin());
    }

    @Override
    @Transactional
    public ShippingSettingsDTO updateZoneFee(Long zoneId, BigDecimal flatFee) {
        ShippingZone zone = zoneRepository.findById(zoneId)
                .filter(z -> !Boolean.TRUE.equals(z.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Shipping zone not found: " + zoneId));
        if (flatFee == null || flatFee.signum() < 0) {
            throw new InvalidRequestException("The shipping price can't be negative.");
        }
        zone.setFlatFee(flatFee.setScale(2, RoundingMode.HALF_UP));
        zoneRepository.save(zone);
        return settings();
    }

    @Override
    @Transactional
    public ShippingSettingsDTO updateRules(ShippingRulesChangeDTO change) {
        StoreSettings s = settingsRepository.findById(StoreSettings.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Store settings are not set up yet."));
        if (change.offerMin() != null) {
            if (change.offerMin().signum() <= 0) {
                throw new InvalidRequestException("The free-shipping amount must be more than ৳0.");
            }
            s.setShippingOfferMin(change.offerMin().setScale(2, RoundingMode.HALF_UP));
        }
        if (change.offerEnabled() != null) {
            if (change.offerEnabled() && (s.getShippingOfferMin() == null || s.getShippingOfferMin().signum() <= 0)) {
                throw new InvalidRequestException("Set the order amount for free shipping before turning the offer on.");
            }
            s.setShippingOfferEnabled(change.offerEnabled());
        }
        if (change.freeAll() != null) {
            s.setShippingFreeAll(change.freeAll());
        }
        settingsRepository.save(s);
        return settings();
    }

    @Override
    @Transactional
    public BulkShippingChargeResultDTO setCharges(BulkShippingChargeRequestDTO request) {
        BigDecimal charge = request.isUseAreaPrice() ? null : request.getCharge();
        if (!request.isUseAreaPrice()) {
            if (charge == null) {
                throw new InvalidRequestException("Type a delivery charge, or choose to use the area price.");
            }
            if (charge.signum() < 0) {
                throw new InvalidRequestException("A delivery charge can't be negative.");
            }
            charge = charge.setScale(2, RoundingMode.HALF_UP);
        }

        return switch (request.getScope()) {
            case PRODUCTS -> setOnProducts(request.getProductIds(), charge);
            case CATEGORY -> setOnCategory(request.getCategoryId(), charge);
            case ALL -> setOnWholeShop(charge);
        };
    }

    /** The products ticked on the Products page get a charge of their own. */
    private BulkShippingChargeResultDTO setOnProducts(List<Long> ids, BigDecimal charge) {
        if (ids == null || ids.isEmpty()) {
            throw new InvalidRequestException("Choose at least one product.");
        }
        List<Product> products = productRepository.findAllById(ids).stream()
                .filter(p -> !Boolean.TRUE.equals(p.getDeleted()))
                .toList();
        if (products.isEmpty()) {
            throw new InvalidRequestException("There are no products to change.");
        }
        products.forEach(p -> p.setShippingCharge(charge));
        productRepository.saveAll(products);

        String what = products.size() + " product" + (products.size() == 1 ? "" : "s");
        return BulkShippingChargeResultDTO.builder()
                .products(products.size())
                .scopeLabel("the products you chose")
                .message(charge == null
                        ? what + " now use their area's delivery price."
                        : describe(charge) + " now set on " + what + ".")
                .build();
    }

    /**
     * The charge goes on the category itself, not on its products, so anything
     * added to it later is covered too. A product with a charge of its own keeps
     * it -- the nearest rule always wins -- and the message says how many.
     */
    private BulkShippingChargeResultDTO setOnCategory(Long categoryId, BigDecimal charge) {
        if (categoryId == null) {
            throw new InvalidRequestException("Choose a category.");
        }
        Category category = categoryRepository.findById(categoryId)
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
        category.setShippingCharge(charge);
        categoryRepository.save(category);

        // Its subcategories follow it too, except any that set a charge of their own.
        java.util.Set<Long> following = new java.util.LinkedHashSet<>();
        following.add(categoryId);
        int subcategoriesOwn = 0;
        // Walk down level by level; a subcategory with its own charge keeps it,
        // and so does everything under that subcategory.
        java.util.Deque<Long> toVisit = new java.util.ArrayDeque<>(List.of(categoryId));
        while (!toVisit.isEmpty()) {
            for (Category sub : categoryRepository.findByParentIdAndDeletedFalse(toVisit.poll())) {
                if (sub.getShippingCharge() != null) { subcategoriesOwn++; continue; }
                if (following.add(sub.getId())) toVisit.add(sub.getId());
            }
        }
        List<Product> inCategory = productRepository.findLiveByCategories(following);
        int keptOwn = (int) inCategory.stream().filter(p -> p.getShippingCharge() != null).count();
        int follow = inCategory.size() - keptOwn;

        StringBuilder message = new StringBuilder(charge == null
                ? category.getName() + " now uses the area's delivery price."
                : category.getName() + " now costs " + taka(charge) + " to deliver.");
        message.append(' ').append(follow).append(follow == 1 ? " product follows" : " products follow")
                .append(" it, and anything you add to it later.");
        if (keptOwn > 0) {
            message.append(' ').append(keptOwn)
                    .append(keptOwn == 1 ? " product has its own charge and keeps it."
                            : " products have their own charge and keep it.");
        }
        if (subcategoriesOwn > 0) {
            message.append(' ').append(subcategoriesOwn)
                    .append(subcategoriesOwn == 1 ? " subcategory has its own charge and keeps it."
                            : " subcategories have their own charge and keep it.");
        }
        return BulkShippingChargeResultDTO.builder()
                .categories(1)
                .keptOwnCharge(keptOwn)
                .scopeLabel(category.getName())
                .message(message.toString())
                .build();
    }

    /**
     * Every product in the shop. Taking the charge away here also clears the
     * categories', because otherwise "use the area price for the whole shop"
     * would not be true afterwards -- an inherited charge would still apply.
     */
    private BulkShippingChargeResultDTO setOnWholeShop(BigDecimal charge) {
        List<Product> products = productRepository.findAll().stream()
                .filter(p -> !Boolean.TRUE.equals(p.getDeleted()))
                .toList();
        if (products.isEmpty()) {
            throw new InvalidRequestException("There are no products to change.");
        }
        products.forEach(p -> p.setShippingCharge(charge));
        productRepository.saveAll(products);

        int categoriesCleared = 0;
        if (charge == null) {
            List<Category> withCharge = categoryRepository.findAll().stream()
                    .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                    .filter(c -> c.getShippingCharge() != null)
                    .toList();
            withCharge.forEach(c -> c.setShippingCharge(null));
            categoryRepository.saveAll(withCharge);
            categoriesCleared = withCharge.size();
        }

        String message = charge == null
                ? "Every product now uses its area's delivery price."
                : describe(charge) + " now set on every product in the shop.";
        if (categoriesCleared > 0) {
            message += " " + categoriesCleared + (categoriesCleared == 1 ? " category charge" : " category charges")
                    + " were cleared as well.";
        }
        return BulkShippingChargeResultDTO.builder()
                .products(products.size())
                .categories(categoriesCleared)
                .scopeLabel("the whole shop")
                .message(message)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal computeTax(Long zoneId, BigDecimal taxableAmount) {
        if (taxableAmount == null || taxableAmount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        TaxRule rule = taxRepository.findApplicableForZone(zoneId).orElse(null);
        if (rule == null || Boolean.TRUE.equals(rule.getIsInclusive())) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = rule.getRate() == null ? BigDecimal.ZERO : rule.getRate();
        return taxableAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** "Free delivery" or "150 taka delivery", for the message the admin reads. */
    private static String describe(BigDecimal charge) {
        return charge.signum() == 0 ? "Free delivery" : taka(charge) + " delivery";
    }

    private static String taka(BigDecimal amount) {
        return "৳" + new DecimalFormat("#,##0.##").format(amount);
    }

    private boolean matchesAnyKeyword(String lowercaseAddress, String csv) {
        for (String raw : csv.split(",")) {
            String kw = raw.trim().toLowerCase();
            if (!kw.isEmpty() && lowercaseAddress.contains(kw)) return true;
        }
        return false;
    }

    private ShippingZoneDTO toDTO(ShippingZone z) {
        return ShippingZoneDTO.builder()
                .id(z.getId())
                .code(z.getCode())
                .displayName(z.getDisplayName())
                .countryCode(z.getCountryCode())
                .flatFee(z.getFlatFee())
                .isDefault(z.getIsDefault())
                .isActive(z.getIsActive())
                .build();
    }
}
