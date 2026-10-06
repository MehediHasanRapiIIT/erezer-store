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
        return variantRepository.findByProductId(productId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public VariantResponseDTO create(Long productId, VariantRequestDTO request) {
        Product product = ensureProductExists(productId);
        refuseSizesAlreadyThere(productId, List.of(request));
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
            if (!seen.add(size.toUpperCase())) {
                throw new InvalidRequestException("Size " + size + " is listed twice.");
            }
        }
        refuseSizesAlreadyThere(productId, requests);
        return requests.stream().map(r -> create(productId, r)).toList();
    }

    /** A product can't have the same size twice: customers would see it twice. */
    private void refuseSizesAlreadyThere(Long productId, List<VariantRequestDTO> requests) {
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (Variant v : variantRepository.findByProductId(productId)) {
            if (v.getSize() != null && !Boolean.TRUE.equals(v.getDeleted())) {
                existing.add(v.getSize().trim().toUpperCase());
            }
        }
        for (VariantRequestDTO r : requests) {
            String size = trim(r.getSize());
            if (size != null && existing.contains(size.toUpperCase())) {
                throw new InvalidRequestException("This product already has size " + size + ".");
            }
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
            boolean taken = variantRepository.findByProductId(productId).stream()
                    .filter(other -> !other.getId().equals(variantId) && !Boolean.TRUE.equals(other.getDeleted()))
                    .anyMatch(other -> other.getSize() != null && other.getSize().trim().equalsIgnoreCase(newSize));
            if (taken) {
                throw new InvalidRequestException("This product already has size " + newSize + ".");
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
        v.setDeleted(true);
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

        String name = trim(r.getName());
        if (name == null) {
            name = v.getSize();
        }
        v.setName(name);

        // SKU: use the admin's override if given, else auto-derive from the
        // product SKU + size. Either way, guarantee per-product uniqueness.
        String sku = trim(r.getSku());
        if (sku == null) {
            sku = autoSku(product, v.getSize());
        }
        v.setSku(ensureUniqueSku(product.getId(), sku, v.getId()));
    }

    /** Derive a variant SKU like "ER-00006-M" from the product SKU + size. */
    private String autoSku(Product product, String size) {
        String base = product.getSku() != null ? product.getSku() : "PR-" + product.getId();
        StringBuilder sb = new StringBuilder(base);
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
                .sku(v.getSku())
                .stockQuantity(v.getStockQuantity())
                .priceOverride(v.getPriceOverride())
                .build();
    }
}
