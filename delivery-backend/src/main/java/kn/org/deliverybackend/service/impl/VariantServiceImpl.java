package kn.org.deliverybackend.service.impl;

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
        ensureProductExists(productId);
        // Drop Shoulder's sizes, then Regular Fit's; S before M before L in each.
        return variantRepository.findByProductId(productId).stream()
                .sorted(java.util.Comparator
                        .comparingInt((Variant v) -> kn.org.deliverybackend.enumeration.Fit.rank(v.getFit()))
                        .thenComparingInt(v -> sizeRank(v.getSize())))
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public VariantResponseDTO create(Long productId, VariantRequestDTO request) {
        Product product = ensureProductExists(productId);
        refuseSizesAlreadyThere(productId, List.of(request));
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
        applyFields(v, request, product);
        Variant saved = variantRepository.saveAndFlush(v);
        inventoryService.followSizes(productId);
        return toDTO(saved);
    }

    @Override
    @Transactional
    public List<VariantResponseDTO> createAll(Long productId, List<VariantRequestDTO> requests) {
        ensureProductExists(productId);
        if (requests == null || requests.isEmpty()) {
            throw new InvalidRequestException("Choose at least one size.");
        }
        // Every size is checked before the first is saved, so a mistake in the
        // last one never leaves the first few behind.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (VariantRequestDTO r : requests) {
            String size = trim(r.getSize());
            if (size == null) {
                throw new InvalidRequestException("Every row needs a size.");
            }
            if (!seen.add(key(fitOf(r), size))) {
                throw new InvalidRequestException(describe(fitOf(r), size) + " is listed twice.");
            }
        }
        refuseSizesAlreadyThere(productId, requests);
        refuseMixingFits(productId, requests);
        return requests.stream().map(r -> create(productId, r)).toList();
    }

    /** A product can't have the same size twice: customers would see it twice. */
    private void refuseSizesAlreadyThere(Long productId, List<VariantRequestDTO> requests) {
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (Variant v : variantRepository.findByProductId(productId)) {
            if (v.getSize() != null && !Boolean.TRUE.equals(v.getDeleted())) {
                existing.add(key(v.getFit(), v.getSize()));
            }
        }
        for (VariantRequestDTO r : requests) {
            String size = trim(r.getSize());
            if (size != null && existing.contains(key(fitOf(r), size))) {
                throw new InvalidRequestException("This product already has " + describe(fitOf(r), size) + ".");
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

    private static String key(String fit, String size) {
        return (fit == null ? "" : fit) + "|" + (size == null ? "" : size.trim().toUpperCase());
    }

    /** "Drop Shoulder M" or "size M", for a message. */
    private static String describe(String fit, String size) {
        String label = kn.org.deliverybackend.enumeration.Fit.labelOf(fit);
        return label == null ? "size " + size : label + " " + size;
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
            removeFits(live);
        } else {
            String first = wanted.keySet().iterator().next();
            // The sizes the product comes in, read before any fit is taken away.
            java.util.LinkedHashSet<String> sizes = new java.util.LinkedHashSet<>();
            live.forEach(v -> sizes.add(v.getSize()));
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
                for (String size : sizes) {
                    boolean there = live.stream().anyMatch(v -> fit.equals(v.getFit())
                            && size != null && size.equalsIgnoreCase(v.getSize()));
                    if (there) continue;
                    Variant v = new Variant();
                    v.setProductId(productId);
                    v.setCategoryId(product.getCategoryId());
                    v.setShopId(product.getShopId());
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
    private void removeFits(List<Variant> live) {
        String keep = live.stream().map(Variant::getFit).filter(java.util.Objects::nonNull)
                .min(java.util.Comparator.comparingInt(kn.org.deliverybackend.enumeration.Fit::rank)).orElse(null);
        if (keep == null) return;
        for (Variant v : live) {
            if (v.getFit() == null || keep.equals(v.getFit())) continue;
            Variant twin = live.stream()
                    .filter(k -> keep.equals(k.getFit()) && k.getSize() != null && k.getSize().equalsIgnoreCase(v.getSize()))
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
                v.setName(v.getSize());
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
    private static void retire(Variant v) {
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
        v.setName(kn.org.deliverybackend.enumeration.Fit.describe(fit, v.getSize()));
        if (v.getSku() == null) {
            v.setSku(ensureUniqueSku(product.getId(), autoSku(product, fit, v.getSize()), v.getId()));
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
            // The same size may exist in the other fit; it can't be twice in this one.
            boolean taken = variantRepository.findByProductId(productId).stream()
                    .filter(other -> !other.getId().equals(variantId) && !Boolean.TRUE.equals(other.getDeleted()))
                    .filter(other -> java.util.Objects.equals(other.getFit(), v.getFit()))
                    .anyMatch(other -> other.getSize() != null && other.getSize().trim().equalsIgnoreCase(newSize));
            if (taken) {
                throw new InvalidRequestException("This product already has " + describe(v.getFit(), newSize) + ".");
            }
        }
        applyFields(v, request, product);
        Variant saved = variantRepository.saveAndFlush(v);
        inventoryService.followSizes(productId);
        return toDTO(saved);
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
            name = kn.org.deliverybackend.enumeration.Fit.describe(v.getFit(), v.getSize());
        }
        v.setName(name);

        // SKU: use the admin's override if given, else auto-derive from the
        // product SKU + size. Either way, guarantee per-product uniqueness.
        String sku = trim(r.getSku());
        if (sku == null) {
            sku = autoSku(product, v.getFit(), v.getSize());
        }
        v.setSku(ensureUniqueSku(product.getId(), sku, v.getId()));
    }

    /** Derive a variant SKU like "ER-00006-M", or "ER-00006-DS-M" in a fit, from the product SKU. */
    private String autoSku(Product product, String fit, String size) {
        String base = product.getSku() != null ? product.getSku() : "PR-" + product.getId();
        StringBuilder sb = new StringBuilder(base);
        if (fit != null) {
            try {
                sb.append('-').append(kn.org.deliverybackend.enumeration.Fit.valueOf(fit).code());
            } catch (IllegalArgumentException ignored) {
                // a fit this version no longer knows: the SKU goes without it
            }
        }
        if (size != null)  sb.append('-').append(slug(size));
        return sb.toString();
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

    private VariantResponseDTO toDTO(Variant v) {
        return VariantResponseDTO.builder()
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
