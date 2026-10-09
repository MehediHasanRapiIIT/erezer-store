package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.variant.ProductOptionDTO;
import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import kn.org.deliverybackend.dto.variant.VariantResponseDTO;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import kn.org.deliverybackend.service.InventoryService;
import kn.org.deliverybackend.service.VariantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A product's own options - colour, and anything else the shop defines - and
 * what changing them does to the variants the product already has.
 *
 * <p>The rule throughout: stock that has been counted is never thrown away by
 * accident, and a variant the shop removed on purpose does not come back by
 * itself.
 *
 * <ul>
 *   <li>The first options on a product: what it has now becomes the first
 *       choice of each (the black one, if Black is listed first), keeping its
 *       stock, price and SKU. Every other combination is added with no stock.</li>
 *   <li>A choice added: its combinations are added, with no stock.</li>
 *   <li>A choice removed: its variants go, with their stock.</li>
 *   <li>An option removed: variants that now differ in nothing are merged into
 *       one, and their stock added together.</li>
 *   <li>A name changed: nothing but the name changes.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ProductOptionsService {

    /** The most variants one product has. A guard against a typo, not a sales limit. */
    public static final int MAX_VARIANTS = 500;
    /** Names the product's Fit and Size sections already own. */
    private static final Set<String> TAKEN = Set.of("size", "sizes", "fit", "fits");

    private final ProductRepository productRepository;
    private final VariantRepository variantRepository;
    private final VariantService variantService;
    private final InventoryService inventoryService;
    private final kn.org.deliverybackend.repository.ProductImageRepository imageRepository;

    /** A product's options and its variants as they stand. */
    public record Result(List<ProductOptionDTO> options, List<VariantResponseDTO> variants) {
    }

    @Transactional(readOnly = true)
    public List<ProductOptionDTO> get(Long productId) {
        return ProductOptions.parse(load(productId).getOptionsJson());
    }

    /**
     * Sets a product's options to exactly these. {@code restoreMissing} also
     * puts back any combination the product does not have - the ones removed
     * earlier on purpose.
     */
    @Transactional
    public Result set(Long productId, List<ProductOptionDTO> requested, boolean restoreMissing) {
        Product product = load(productId);
        List<ProductOptionDTO> before = ProductOptions.parse(product.getOptionsJson());
        List<ProductOptionDTO> after = checked(requested, before);

        List<Variant> live = new ArrayList<>(variantRepository.findByProductId(productId));
        boolean hadNone = live.isEmpty();

        Set<String> optionsBefore = ids(before);
        Set<String> optionsAfter = ids(after);
        Map<String, Set<String>> valuesAfter = new LinkedHashMap<>();
        for (ProductOptionDTO o : after) valuesAfter.put(o.getId(), valueIds(o));
        Set<String> valuesBefore = new HashSet<>();
        for (ProductOptionDTO o : before) valuesBefore.addAll(valueIds(o));

        // How many variants this would make, before anything is touched.
        long slots = Math.max(1, live.stream().map(v -> fitAndSize(v)).distinct().count());
        long combinations = ProductOptions.count(after);
        if (combinations * slots > MAX_VARIANTS) {
            throw new InvalidRequestException("That would make " + (combinations * slots) + " variants of this product. The most is "
                    + MAX_VARIANTS + ". Use fewer options or fewer choices.");
        }

        // 1. A choice that is gone takes its variants with it.
        for (Variant v : live) {
            for (Map.Entry<String, String> choice : ProductOptions.choices(v.getOptionKey()).entrySet()) {
                Set<String> still = valuesAfter.get(choice.getKey());
                if (still != null && !still.contains(choice.getValue())) VariantServiceImpl.retire(v);
            }
        }

        // 2. An option that is gone: variants left differing in nothing become one.
        boolean optionRemoved = optionsBefore.stream().anyMatch(id -> !optionsAfter.contains(id));
        if (optionRemoved) {
            List<String> orderBefore = ProductOptions.combinations(before);
            Map<String, List<Variant>> same = new LinkedHashMap<>();
            for (Variant v : live) {
                if (Boolean.TRUE.equals(v.getDeleted())) continue;
                Map<String, String> kept = ProductOptions.choices(v.getOptionKey());
                kept.keySet().removeIf(id -> !optionsAfter.contains(id));
                same.computeIfAbsent(ProductOptions.key(kept) + "~" + fitAndSize(v), k -> new ArrayList<>()).add(v);
            }
            for (List<Variant> group : same.values()) {
                // The one from the first choice of what was removed is kept.
                group.sort(Comparator.comparingInt((Variant v) -> { int i = orderBefore.indexOf(v.getOptionKey()); return i < 0 ? Integer.MAX_VALUE : i; })
                        .thenComparing(Variant::getId, Comparator.nullsLast(Comparator.naturalOrder())));
                Variant keeper = group.get(0);
                Map<String, String> kept = ProductOptions.choices(keeper.getOptionKey());
                kept.keySet().removeIf(id -> !optionsAfter.contains(id));
                for (Variant other : group.subList(1, group.size())) {
                    keeper.setStockQuantity(stock(keeper) + stock(other));
                    VariantServiceImpl.retire(other);
                }
                keeper.setOptionKey(ProductOptions.key(kept));
            }
        }

        // 3. An option that is new: what the product has is its first choice.
        for (ProductOptionDTO option : after) {
            if (optionsBefore.contains(option.getId())) continue;
            String first = option.getValues().get(0).getId();
            for (Variant v : live) {
                if (Boolean.TRUE.equals(v.getDeleted())) continue;
                Map<String, String> choices = ProductOptions.choices(v.getOptionKey());
                choices.put(option.getId(), first);
                v.setOptionKey(ProductOptions.key(choices));
            }
        }

        product.setOptionsJson(ProductOptions.write(after));
        productRepository.save(product);
        for (Variant v : live) {
            if (!Boolean.TRUE.equals(v.getDeleted())) {
                v.setName(ProductOptions.variantName(after, v.getOptionKey(), v.getFit(), v.getSize()));
            }
        }
        variantRepository.saveAll(live);
        variantRepository.flush();

        // 4. Combinations the product should now have and doesn't.
        if (!after.isEmpty()) {
            List<Variant> kept = live.stream().filter(v -> !Boolean.TRUE.equals(v.getDeleted())).toList();
            // Each fit and size the product is sold in; one empty slot for a product with neither.
            List<String[]> fitsAndSizes = new ArrayList<>();
            Set<String> seenSlots = new HashSet<>();
            for (Variant v : kept) {
                if (seenSlots.add(fitAndSize(v))) fitsAndSizes.add(new String[] { v.getFit(), v.getSize() });
            }
            if (fitsAndSizes.isEmpty()) fitsAndSizes.add(new String[] { null, null });
            Set<String> have = new HashSet<>();
            kept.forEach(v -> have.add(v.getOptionKey() + "~" + fitAndSize(v)));

            for (String combination : ProductOptions.combinations(after)) {
                boolean brandNew = ProductOptions.choices(combination).values().stream().anyMatch(id -> !valuesBefore.contains(id));
                if (!(restoreMissing || hadNone || brandNew)) continue;
                for (String[] slot : fitsAndSizes) {
                    if (have.contains(combination + "~" + slot[0] + "~" + (slot[1] == null ? "" : slot[1].trim().toUpperCase(Locale.ROOT)))) continue;
                    VariantRequestDTO request = new VariantRequestDTO();
                    request.setOptions(ProductOptions.choices(combination));
                    request.setFit(slot[0]);
                    request.setSize(slot[1]);
                    request.setStockQuantity(0);
                    variantService.create(productId, request);
                }
            }
        }

        // A picture of a choice that is gone suits every choice again.
        Set<String> valuesNow = new HashSet<>();
        valuesAfter.values().forEach(valuesNow::addAll);
        for (var image : imageRepository.findBelongingToAChoice(productId)) {
            if (!valuesNow.contains(image.getOptionValueId())) {
                image.setOptionValueId(null);
                imageRepository.save(image);
            }
        }

        inventoryService.followSizes(productId);
        return new Result(after, variantService.listForProduct(productId));
    }

    // ── checking what was asked for ──────────────────────────────────────────

    /**
     * The options as they may be stored: named, each with at least one choice,
     * no name twice. An id is kept only when it is one the product already had;
     * anything else is new and gets an id of its own.
     */
    private static List<ProductOptionDTO> checked(List<ProductOptionDTO> requested, List<ProductOptionDTO> before) {
        Map<String, ProductOptionDTO> known = new LinkedHashMap<>();
        for (ProductOptionDTO o : before) known.put(o.getId(), o);

        List<ProductOptionDTO> options = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<String> usedIds = new HashSet<>();
        for (ProductOptionDTO r : requested == null ? List.<ProductOptionDTO>of() : requested) {
            if (r == null) continue;
            String name = clean(r.getName());
            if (name == null) throw new InvalidRequestException("Give every option a name, such as Colour.");
            if (name.length() > 40) throw new InvalidRequestException("An option's name can be up to 40 letters.");
            if (TAKEN.contains(name.toLowerCase(Locale.ROOT))) {
                throw new InvalidRequestException("Sizes and fits have their own sections on the product. Use those, not an option called " + name + ".");
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) throw new InvalidRequestException("Two options are called " + name + ". Keep one.");

            ProductOptionDTO was = r.getId() == null ? null : known.get(r.getId());
            ProductOptionDTO option = new ProductOptionDTO();
            option.setId(was != null && usedIds.add(was.getId()) ? was.getId() : ProductOptions.newId());
            option.setName(name);
            option.setKind("COLOUR".equalsIgnoreCase(r.getKind()) ? "COLOUR" : "TEXT");

            Set<String> wasValues = was == null ? Set.of() : valueIds(was);
            Set<String> valueNames = new HashSet<>();
            Set<String> usedValueIds = new HashSet<>();
            for (ProductOptionDTO.Value rv : r.getValues() == null ? List.<ProductOptionDTO.Value>of() : r.getValues()) {
                if (rv == null) continue;
                String value = clean(rv.getValue());
                if (value == null) continue;
                if (value.length() > 40) throw new InvalidRequestException("A choice can be up to 40 letters.");
                if (!valueNames.add(value.toLowerCase(Locale.ROOT))) {
                    throw new InvalidRequestException(name + " has " + value + " twice. Keep one.");
                }
                String id = rv.getId() != null && wasValues.contains(rv.getId()) && usedValueIds.add(rv.getId())
                        ? rv.getId() : ProductOptions.newId();
                option.getValues().add(new ProductOptionDTO.Value(id, value, "COLOUR".equals(option.getKind()) ? hex(rv.getHex()) : null));
            }
            if (option.getValues().isEmpty()) {
                throw new InvalidRequestException("Give " + name + " at least one choice.");
            }
            options.add(option);
        }
        return options;
    }

    /** "#1f2937" as "#1F2937"; null for anything that is not a colour. */
    private static String hex(String raw) {
        if (raw == null) return null;
        String hex = raw.trim().toUpperCase(Locale.ROOT);
        return hex.matches("#[0-9A-F]{6}") ? hex : null;
    }

    private static String clean(String raw) {
        if (raw == null) return null;
        String text = raw.trim().replaceAll("\\s+", " ");
        return text.isEmpty() ? null : text;
    }

    private static Set<String> ids(List<ProductOptionDTO> options) {
        Set<String> ids = new HashSet<>();
        options.forEach(o -> ids.add(o.getId()));
        return ids;
    }

    private static Set<String> valueIds(ProductOptionDTO option) {
        Set<String> ids = new HashSet<>();
        option.getValues().forEach(v -> ids.add(v.getId()));
        return ids;
    }

    private static String fitAndSize(Variant v) {
        return Objects.toString(v.getFit(), "null") + "~" + (v.getSize() == null ? "" : v.getSize().trim().toUpperCase(Locale.ROOT));
    }

    private static int stock(Variant v) {
        return v.getStockQuantity() == null ? 0 : v.getStockQuantity();
    }

    private Product load(Long productId) {
        return productRepository.findById(productId)
                .filter(p -> !Boolean.TRUE.equals(p.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }
}
