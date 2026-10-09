package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.variant.ProductOptionDTO;
import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import kn.org.deliverybackend.dto.variant.VariantResponseDTO;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.exception.DuplicateResourceException;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import kn.org.deliverybackend.service.InventoryService;
import kn.org.deliverybackend.service.VariantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VariantServiceImpl implements VariantService {

    private final VariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    @Override
    @Transactional(readOnly = true)
    public List<VariantResponseDTO> listForProduct(Long productId) {
        Product product = ensureProductExists(productId);
        List<ProductOptionDTO> options = ProductOptions.parse(product.getOptionsJson());
        List<String> order = ProductOptions.combinations(options);
        // Each combination of the product's own options in turn (Black, then
        // White); within it Drop Shoulder's sizes, then Regular Fit's; S before
        // M before L in each.
        return variantRepository.findByProductId(productId).stream()
                .sorted(java.util.Comparator
                        .comparingInt((Variant v) -> { int i = order.indexOf(v.getOptionKey()); return i < 0 ? order.size() : i; })
                        .thenComparingInt(v -> kn.org.deliverybackend.enumeration.Fit.rank(v.getFit()))
                        .thenComparingInt(v -> sizeRank(v.getSize())))
                .map(v -> toDTO(v, options))
                .toList();
    }

    @Override
    @Transactional
    public VariantResponseDTO create(Long productId, VariantRequestDTO request) {
        Product product = ensureProductExists(productId);
        String optionKey = optionKeyOf(product, request);
        refuseSizesAlreadyThere(product, List.of(request));
        refuseMixingFits(productId, List.of(request));
        if (request.getSku() != null && !request.getSku().isBlank()) {
            variantRepository.findByProductIdAndSku(productId, request.getSku()).ifPresent(existing -> {
                throw new DuplicateResourceException(
                        "SKU '" + request.getSku() + "' already exists for this product.");
            });
        }
        Variant v = new Variant();
        v.setProductId(productId);
        v.setCategoryId(product.getCategoryId());
        v.setShopId(product.getShopId());
        v.setOptionKey(optionKey);
        applyFields(v, request, product);
        Variant saved = variantRepository.saveAndFlush(v);
        inventoryService.followSizes(productId);
        return toDTO(saved, ProductOptions.parse(product.getOptionsJson()));
    }

    /**
     * The combination a new variant is given, checked. A product with options
     * needs a choice for every one of them; a product without takes none.
     */
    private static String optionKeyOf(Product product, VariantRequestDTO request) {
        List<ProductOptionDTO> options = ProductOptions.parse(product.getOptionsJson());
        String key = ProductOptions.key(request.getOptions());
        if (options.isEmpty()) {
            if (key != null) throw new InvalidRequestException("This product has no options to choose from.");
            return null;
        }
        if (!ProductOptions.allows(options, key)) {
            throw new InvalidRequestException("Choose one of each of this product's options: "
                    + String.join(", ", options.stream().map(ProductOptionDTO::getName).toList()) + ".");
        }
        return key;
    }

    /**
     * The combinations a product's variants are in right now, in the order they
     * are shown; one null entry for a product with no options. Used to give a
     * new size, or a new fit, to every combination the product is sold in.
     */
    private List<String> combinationsInUse(Product product, List<Variant> live) {
        List<ProductOptionDTO> options = ProductOptions.parse(product.getOptionsJson());
        if (options.isEmpty()) return java.util.Collections.singletonList(null);
        List<String> all = ProductOptions.combinations(options);
        java.util.Set<String> used = new java.util.HashSet<>();
        live.forEach(v -> used.add(v.getOptionKey()));
        // Nothing sold yet: every combination. Otherwise only those the shop
        // kept, so one it removed on purpose does not come back with a new size.
        List<String> kept = all.stream().filter(used::contains).toList();
        return kept.isEmpty() ? all : kept;
    }

    @Override
    @Transactional
    public List<VariantResponseDTO> createAll(Long productId, List<VariantRequestDTO> requests) {
        Product product = ensureProductExists(productId);
        if (requests == null || requests.isEmpty()) {
            throw new InvalidRequestException("Choose at least one size.");
        }
        // A size added without saying which combination is meant for all of
        // them: "add size XL" on a shirt in three colours is XL in each.
        List<ProductOptionDTO> options = ProductOptions.parse(product.getOptionsJson());
        if (!options.isEmpty()) {
            List<String> inUse = combinationsInUse(product, variantRepository.findByProductId(productId));
            List<VariantRequestDTO> spread = new java.util.ArrayList<>();
            for (VariantRequestDTO r : requests) {
                if (r.getOptions() != null && !r.getOptions().isEmpty()) {
                    spread.add(r);
                    continue;
                }
                for (String combination : inUse) {
                    VariantRequestDTO copy = new VariantRequestDTO();
                    org.springframework.beans.BeanUtils.copyProperties(r, copy);
                    copy.setOptions(ProductOptions.choices(combination));
                    // One SKU can't be on several variants: each gets its own, made for it.
                    if (inUse.size() > 1) copy.setSku(null);
                    spread.add(copy);
                }
            }
            requests = takeUpSizeless(product, spread);
            if (requests.isEmpty()) return listForProduct(productId);
        }
        // Every size is checked before the first is saved, so a mistake in the
        // last one never leaves the first few behind.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (VariantRequestDTO r : requests) {
            String size = trim(r.getSize());
            // A product with options can be sold without sizes: a cap in three colours.
            if (size == null && options.isEmpty()) {
                throw new InvalidRequestException("Every row needs a size.");
            }
            String optionKey = optionKeyOf(product, r);
            if (!seen.add(key(optionKey, fitOf(r), size))) {
                throw new InvalidRequestException(describe(options, optionKey, fitOf(r), size) + " is listed twice.");
            }
        }
        refuseSizesAlreadyThere(product, requests);
        refuseMixingFits(productId, requests);
        List<VariantResponseDTO> made = new java.util.ArrayList<>();
        for (VariantRequestDTO r : requests) made.add(create(productId, r));
        return made;
    }

    /**
     * A product sold in colours but no sizes has one variant for each colour.
     * When it is given sizes, those variants become the first size asked for -
     * keeping their stock if they have any, otherwise taking what was typed -
     * rather than being left beside the sized ones as a colour with no size.
     * Returns the requests that still need a variant of their own.
     */
    private List<VariantRequestDTO> takeUpSizeless(Product product, List<VariantRequestDTO> requests) {
        List<VariantRequestDTO> left = new java.util.ArrayList<>(requests);
        for (Variant v : variantRepository.findByProductId(product.getId())) {
            if (v.getSize() != null || v.getFit() != null) continue;
            VariantRequestDTO first = left.stream()
                    .filter(r -> trim(r.getSize()) != null
                            && java.util.Objects.equals(ProductOptions.key(r.getOptions()), v.getOptionKey()))
                    .findFirst().orElse(null);
            if (first == null) continue;
            v.setSize(trim(first.getSize()));
            v.setFit(fitOf(first));
            if (stock(v) == 0 && first.getStockQuantity() != null) v.setStockQuantity(first.getStockQuantity());
            if (v.getPriceOverride() == null) v.setPriceOverride(first.getPriceOverride());
            v.setName(nameOf(product, v));
            // Its SKU said only the colour; now it says the size too.
            v.setSku(ensureUniqueSku(product.getId(), autoSku(product, v.getOptionKey(), v.getFit(), v.getSize()), v.getId()));
            variantRepository.saveAndFlush(v);
            left.remove(first);
        }
        if (left.size() != requests.size()) inventoryService.followSizes(product.getId());
        return left;
    }

    /** A product can't have the same variant twice: customers would see it twice. */
    private void refuseSizesAlreadyThere(Product product, List<VariantRequestDTO> requests) {
        List<ProductOptionDTO> options = ProductOptions.parse(product.getOptionsJson());
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (Variant v : variantRepository.findByProductId(product.getId())) {
            if (Boolean.TRUE.equals(v.getDeleted())) continue;
            if (v.getSize() != null || v.getOptionKey() != null) {
                existing.add(key(v.getOptionKey(), v.getFit(), v.getSize()));
            }
        }
        for (VariantRequestDTO r : requests) {
            String size = trim(r.getSize());
            String optionKey = ProductOptions.key(r.getOptions());
            if ((size != null || optionKey != null) && existing.contains(key(optionKey, fitOf(r), size))) {
                throw new InvalidRequestException("This product already has " + describe(options, optionKey, fitOf(r), size) + ".");
            }
        }
    }

    /**
     * A product's sizes all belong to a fit, or none do: a customer who picks
     * Drop Shoulder has to find every size of it there, and a size with no fit
     * beside them could never be chosen.
     */
    private void refuseMixingFits(Long productId, List<VariantRequestDTO> requests) {
        boolean withFit = requests.stream().anyMatch(r -> fitOf(r) != null);
        boolean without = requests.stream().anyMatch(r -> fitOf(r) == null);
        if (withFit && without) {
            throw new InvalidRequestException("Give every size a fit, or none of them.");
        }
        List<Variant> live = variantRepository.findByProductId(productId);
        if (live.isEmpty()) return;
        boolean productHasFits = live.stream().anyMatch(v -> v.getFit() != null);
        if (productHasFits && without) {
            throw new InvalidRequestException(
                    "This product comes in fits: say whether the size is Drop Shoulder or Regular Fit.");
        }
        if (!productHasFits && withFit) {
            throw new InvalidRequestException(
                    "This product has sizes with no fit. Choose its fits first, in the Fit section.");
        }
    }

    /** The stored name of a request's fit, or null; an unknown fit is refused. */
    private static String fitOf(VariantRequestDTO r) {
        kn.org.deliverybackend.enumeration.Fit fit = kn.org.deliverybackend.enumeration.Fit.parse(r.getFit());
        return fit == null ? null : fit.name();
    }

    /** What makes a variant itself: its combination of options, its fit and its size. */
    private static String key(String optionKey, String fit, String size) {
        return (optionKey == null ? "" : optionKey) + "~" + (fit == null ? "" : fit) + "~" + (size == null ? "" : size.trim().toUpperCase());
    }

    /** "Drop Shoulder M" or "size M", for a message. */
    private static String describe(String fit, String size) {
        String label = kn.org.deliverybackend.enumeration.Fit.labelOf(fit);
        return label == null ? "size " + size : label + " " + size;
    }

    /** "Black / Long, Drop Shoulder M", for a message. */
    private static String describe(List<ProductOptionDTO> options, String optionKey, String fit, String size) {
        String combination = ProductOptions.label(options, optionKey);
        if (combination == null) return describe(fit, size);
        return size == null && fit == null ? combination : combination + ", " + describe(fit, size);
    }

    /** What a variant is called: "Black / Long / Drop Shoulder / M", from whichever of those it has. */
    private static String nameOf(Product product, Variant v) {
        return ProductOptions.variantName(ProductOptions.parse(product.getOptionsJson()), v.getOptionKey(), v.getFit(), v.getSize());
    }

    // ── fits ───────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public List<VariantResponseDTO> setFits(Long productId,
                                            kn.org.deliverybackend.dto.variant.ProductFitsRequestDTO request) {
        Product product = ensureProductExists(productId);

        // What is asked for, Drop Shoulder first, each fit once.
        java.util.Map<String, kn.org.deliverybackend.dto.variant.ProductFitsRequestDTO.Choice> wanted =
                new java.util.TreeMap<>(java.util.Comparator.comparingInt(kn.org.deliverybackend.enumeration.Fit::rank));
        for (var choice : request.getFits() == null
                ? List.<kn.org.deliverybackend.dto.variant.ProductFitsRequestDTO.Choice>of() : request.getFits()) {
            kn.org.deliverybackend.enumeration.Fit fit = kn.org.deliverybackend.enumeration.Fit.parse(choice.getFit());
            if (fit == null) throw new InvalidRequestException("Which fit?");
            if (wanted.put(fit.name(), choice) != null) {
                throw new InvalidRequestException(fit.label() + " is listed twice.");
            }
        }

        List<Variant> live = new java.util.ArrayList<>(variantRepository.findByProductId(productId));
        if (live.isEmpty()) {
            if (wanted.isEmpty()) return List.of();
            throw new InvalidRequestException("Add the product's sizes first: a fit's stock is kept size by size.");
        }

        if (wanted.isEmpty()) {
            removeFits(live, product);
        } else {
            String first = wanted.keySet().iterator().next();
            // What the product comes in - each size, in each combination of its
            // own options - read before any fit is taken away.
            java.util.LinkedHashMap<String, String[]> slots = new java.util.LinkedHashMap<>();
            live.forEach(v -> slots.putIfAbsent(key(v.getOptionKey(), null, v.getSize()), new String[] { v.getOptionKey(), v.getSize() }));
            // Sizes that had no fit become the first fit, keeping their stock and price.
            for (Variant v : live) {
                if (v.getFit() == null) relabel(v, first, product);
            }
            // A fit no longer offered goes, with its stock.
            for (java.util.Iterator<Variant> it = live.iterator(); it.hasNext(); ) {
                Variant v = it.next();
                if (!wanted.containsKey(v.getFit())) {
                    retire(v);
                    variantRepository.save(v);
                    it.remove();
                }
            }
            variantRepository.flush();
            // Every fit offers every size the product has; a new one starts with none in stock.
            for (String fit : wanted.keySet()) {
                for (String[] slot : slots.values()) {
                    String optionKey = slot[0];
                    String size = slot[1];
                    boolean there = live.stream().anyMatch(v -> fit.equals(v.getFit())
                            && java.util.Objects.equals(optionKey, v.getOptionKey())
                            && size != null && size.equalsIgnoreCase(v.getSize()));
                    if (there || size == null) continue;
                    Variant v = new Variant();
                    v.setProductId(productId);
                    v.setCategoryId(product.getCategoryId());
                    v.setShopId(product.getShopId());
                    v.setOptionKey(optionKey);
                    v.setSize(size);
                    v.setStockQuantity(0);
                    relabel(v, fit, product);
                    live.add(v);
                }
            }
            // A price typed for a fit goes on each of its sizes.
            for (var entry : wanted.entrySet()) {
                if (!entry.getValue().isChangePrice()) continue;
                for (Variant v : live) {
                    if (entry.getKey().equals(v.getFit())) v.setPriceOverride(entry.getValue().getPrice());
                }
            }
            variantRepository.saveAll(live);
        }
        variantRepository.flush();
        inventoryService.followSizes(productId);
        return listForProduct(productId);
    }

    /**
     * Back to a product with no fits. With one fit its sizes simply lose the
     * label. With two, the first fit's sizes are kept and take in the stock of
     * the other's, so nothing counted is lost.
     */
    private void removeFits(List<Variant> live, Product product) {
        String keep = live.stream().map(Variant::getFit).filter(java.util.Objects::nonNull)
                .min(java.util.Comparator.comparingInt(kn.org.deliverybackend.enumeration.Fit::rank)).orElse(null);
        if (keep == null) return;
        for (Variant v : live) {
            if (v.getFit() == null || keep.equals(v.getFit())) continue;
            Variant twin = live.stream()
                    .filter(k -> keep.equals(k.getFit()) && java.util.Objects.equals(k.getOptionKey(), v.getOptionKey())
                            && k.getSize() != null && k.getSize().equalsIgnoreCase(v.getSize()))
                    .findFirst().orElse(null);
            if (twin == null) {
                // A size only the other fit had: it stays, as a plain size.
                v.setFit(keep);
                continue;
            }
            twin.setStockQuantity(stock(twin) + stock(v));
            retire(v);
        }
        for (Variant v : live) {
            if (!Boolean.TRUE.equals(v.getDeleted())) {
                v.setFit(null);
                v.setName(nameOf(product, v));
            }
        }
        variantRepository.saveAll(live);
    }

    /**
     * Takes a size out of use. The row stays, because old orders point at it,
     * but it gives up its SKU: a product's SKUs are unique in the database even
     * among removed sizes, so without this the same size could never be added
     * again.
     */
    static void retire(Variant v) {
        v.setDeleted(true);
        if (v.getSku() != null && v.getId() != null) {
            String suffix = "~" + v.getId();
            String sku = v.getSku();
            v.setSku(sku.substring(0, Math.min(sku.length(), 64 - suffix.length())) + suffix);
        }
    }

    private static int stock(Variant v) {
        return v.getStockQuantity() == null ? 0 : v.getStockQuantity();
    }

    /** Puts a size in a fit: its label and, for a new size, its SKU. */
    private void relabel(Variant v, String fit, Product product) {
        v.setFit(fit);
        v.setName(nameOf(product, v));
        if (v.getSku() == null) {
            v.setSku(ensureUniqueSku(product.getId(), autoSku(product, v.getOptionKey(), fit, v.getSize()), v.getId()));
        }
    }

    @Override
    @Transactional
    public VariantResponseDTO update(Long productId, Long variantId, VariantRequestDTO request) {
        Product product = ensureProductExists(productId);
        Variant v = variantRepository.findById(variantId)
                .filter(x -> !Boolean.TRUE.equals(x.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + variantId));
        if (!productId.equals(v.getProductId())) {
            throw new ResourceNotFoundException("Variant not found for this product: " + variantId);
        }
        if (request.getSku() != null && !request.getSku().isBlank()
                && !request.getSku().equals(v.getSku())) {
            variantRepository.findByProductIdAndSku(productId, request.getSku()).ifPresent(existing -> {
                if (!existing.getId().equals(variantId)) {
                    throw new DuplicateResourceException(
                            "SKU '" + request.getSku() + "' already exists for this product.");
                }
            });
        }
        String newSize = trim(request.getSize());
        if (newSize != null && !newSize.equalsIgnoreCase(v.getSize() == null ? "" : v.getSize().trim())) {
            // The same size may exist in the other fit, or in another combination;
            // it can't be twice in this one.
            boolean taken = variantRepository.findByProductId(productId).stream()
                    .filter(other -> !other.getId().equals(variantId) && !Boolean.TRUE.equals(other.getDeleted()))
                    .filter(other -> java.util.Objects.equals(other.getFit(), v.getFit()))
                    .filter(other -> java.util.Objects.equals(other.getOptionKey(), v.getOptionKey()))
                    .anyMatch(other -> other.getSize() != null && other.getSize().trim().equalsIgnoreCase(newSize));
            if (taken) {
                throw new InvalidRequestException("This product already has " + describe(v.getFit(), newSize) + ".");
            }
        }
        applyFields(v, request, product);
        Variant saved = variantRepository.saveAndFlush(v);
        inventoryService.followSizes(productId);
        return toDTO(saved, ProductOptions.parse(product.getOptionsJson()));
    }

    @Override
    @Transactional
    public void delete(Long productId, Long variantId) {
        ensureProductExists(productId);
        Variant v = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + variantId));
        if (!productId.equals(v.getProductId())) {
            throw new ResourceNotFoundException("Variant not found for this product: " + variantId);
        }
        // Soft-delete (preserves FK integrity with OrderItem.variantId).
        retire(v);
        variantRepository.saveAndFlush(v);
        inventoryService.followSizes(productId);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Product ensureProductExists(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    private void applyFields(Variant v, VariantRequestDTO r, Product product) {
        v.setSize(trim(r.getSize()));
        v.setStockQuantity(r.getStockQuantity() != null ? r.getStockQuantity() : 0);
        v.setPriceOverride(r.getPriceOverride());
        // A new size takes the fit it is given. A size being changed keeps its
        // fit: moving stock between fits is done on the product's Fit section.
        if (v.getId() == null) {
            v.setFit(fitOf(r));
        }

        String name = trim(r.getName());
        if (name == null) {
            name = nameOf(product, v);
        }
        v.setName(name);

        // SKU: use the admin's override if given, else auto-derive from the
        // product SKU + size. Either way, guarantee per-product uniqueness.
        String sku = trim(r.getSku());
        if (sku == null) {
            sku = autoSku(product, v.getOptionKey(), v.getFit(), v.getSize());
        }
        v.setSku(ensureUniqueSku(product.getId(), sku, v.getId()));
    }

    /**
     * Derive a variant SKU like "ER-00006-M", "ER-00006-DS-M" in a fit, or
     * "ER-00006-BLACK-DS-M" in a combination of options, from the product SKU.
     */
    private String autoSku(Product product, String optionKey, String fit, String size) {
        String base = product.getSku() != null ? product.getSku() : "PR-" + product.getId();
        StringBuilder sb = new StringBuilder(base);
        for (var choice : ProductOptions.describe(ProductOptions.parse(product.getOptionsJson()), optionKey)) {
            String word = slug(choice.getValue());
            if (!word.isEmpty()) sb.append('-').append(word.substring(0, Math.min(word.length(), 8)));
        }
        if (fit != null) {
            try {
                sb.append('-').append(kn.org.deliverybackend.enumeration.Fit.valueOf(fit).code());
            } catch (IllegalArgumentException ignored) {
                // a fit this version no longer knows: the SKU goes without it
            }
        }
        if (size != null)  sb.append('-').append(slug(size));
        // The column holds 64 letters, and a clash may still add "-12" to this.
        return sb.length() > 58 ? sb.substring(0, 58) : sb.toString();
    }

    private String slug(String s) {
        return s.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "");
    }

    /**
     * Returns {@code desired} if free for this product, otherwise appends a
     * numeric suffix until unique. {@code selfId} (nullable) is excluded so a
     * variant doesn't collide with itself on update.
     */
    private String ensureUniqueSku(Long productId, String desired, Long selfId) {
        String candidate = desired;
        int suffix = 2;
        while (true) {
            var clash = variantRepository.findByProductIdAndSku(productId, candidate);
            if (clash.isEmpty() || (selfId != null && clash.get().getId().equals(selfId))) {
                return candidate;
            }
            candidate = desired + "-" + suffix++;
        }
    }

    private static final List<String> SIZE_ORDER = List.of("XS", "S", "M", "L", "XL", "XXL", "XXXL");

    private static int sizeRank(String size) {
        int i = size == null ? -1 : SIZE_ORDER.indexOf(size.trim().toUpperCase());
        return i < 0 ? SIZE_ORDER.size() : i;
    }

    private String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private VariantResponseDTO toDTO(Variant v, List<ProductOptionDTO> options) {
        return VariantResponseDTO.builder()
                .optionKey(v.getOptionKey())
                .options(ProductOptions.describe(options, v.getOptionKey()))
                .optionLabel(ProductOptions.label(options, v.getOptionKey()))
                .id(v.getId())
                .productId(v.getProductId())
                .name(v.getName())
                .size(v.getSize())
                .fit(v.getFit())
                .fitLabel(kn.org.deliverybackend.enumeration.Fit.labelOf(v.getFit()))
                .sku(v.getSku())
                .stockQuantity(v.getStockQuantity())
                .priceOverride(v.getPriceOverride())
                .build();
    }
}
