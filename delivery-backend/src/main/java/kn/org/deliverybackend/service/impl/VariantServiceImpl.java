package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import kn.org.deliverybackend.dto.variant.VariantResponseDTO;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.exception.DuplicateResourceException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.VariantRepository;
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
        return toDTO(variantRepository.save(v));
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
        applyFields(v, request, product);
        return toDTO(variantRepository.save(v));
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
        variantRepository.save(v);
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
