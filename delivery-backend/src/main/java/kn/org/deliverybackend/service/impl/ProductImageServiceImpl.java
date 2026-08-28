package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.productimage.ProductImageDTO;
import kn.org.deliverybackend.dto.productimage.ProductImageMetadataDTO;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.ProductImage;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.ProductImageRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.service.FileStorageService;
import kn.org.deliverybackend.service.ProductImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductImageRepository imageRepository;
    private final ProductRepository productRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(readOnly = true)
    public List<ProductImageDTO> listForProduct(Long productId) {
        ensureProductExists(productId);
        return imageRepository.findByProductId(productId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public ProductImageDTO upload(Long productId,
                                   MultipartFile file,
                                   String altText,
                                   Integer sortOrder,
                                   Boolean isPrimary) {
        Product product = ensureProductExists(productId);

        String url = fileStorageService.uploadFile(file);

        // First image uploaded becomes primary unless caller explicitly says otherwise.
        boolean primary = Boolean.TRUE.equals(isPrimary)
                || imageRepository.findPrimaryByProductId(productId).isEmpty();

        if (primary) {
            imageRepository.clearPrimaryFlag(productId);
        }

        ProductImage img = ProductImage.builder()
                .productId(productId)
                .url(url)
                .altText(trim(altText))
                .sortOrder(sortOrder != null ? sortOrder : nextSortOrder(productId))
                .isPrimary(primary)
                .build();
        ProductImage saved = imageRepository.save(img);

        // Keep the legacy Product.imageUrl in sync with the primary image so
        // existing list views (which still read product.imageUrl) keep working.
        if (primary) {
            product.setImageUrl(url);
            productRepository.save(product);
        }

        return toDTO(saved);
    }

    @Override
    @Transactional
    public ProductImageDTO updateMetadata(Long productId, Long imageId, ProductImageMetadataDTO metadata) {
        ensureProductExists(productId);
        ProductImage img = imageRepository.findById(imageId)
                .filter(i -> !Boolean.TRUE.equals(i.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));
        if (!productId.equals(img.getProductId())) {
            throw new ResourceNotFoundException("Image not found for this product: " + imageId);
        }
        if (metadata.getAltText() != null) {
            img.setAltText(trim(metadata.getAltText()));
        }
        if (metadata.getSortOrder() != null) {
            img.setSortOrder(metadata.getSortOrder());
        }
        if (Boolean.TRUE.equals(metadata.getIsPrimary())) {
            imageRepository.clearPrimaryFlag(productId);
            img.setIsPrimary(true);

            // Mirror onto the legacy Product.imageUrl pointer.
            productRepository.findById(productId).ifPresent(p -> {
                p.setImageUrl(img.getUrl());
                productRepository.save(p);
            });
        }
        return toDTO(imageRepository.save(img));
    }

    @Override
    @Transactional
    public void delete(Long productId, Long imageId) {
        ensureProductExists(productId);
        ProductImage img = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));
        if (!productId.equals(img.getProductId())) {
            throw new ResourceNotFoundException("Image not found for this product: " + imageId);
        }
        img.setDeleted(true);
        imageRepository.save(img);

        // If we just removed the primary, promote the next image, or clear the
        // mirrored Product.imageUrl when the gallery is now empty.
        if (Boolean.TRUE.equals(img.getIsPrimary())) {
            List<ProductImage> remaining = imageRepository.findByProductId(productId);
            if (!remaining.isEmpty()) {
                ProductImage next = remaining.get(0);
                next.setIsPrimary(true);
                imageRepository.save(next);
                productRepository.findById(productId).ifPresent(p -> {
                    p.setImageUrl(next.getUrl());
                    productRepository.save(p);
                });
            } else {
                productRepository.findById(productId).ifPresent(p -> {
                    p.setImageUrl(null);
                    productRepository.save(p);
                });
            }
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Product ensureProductExists(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    private int nextSortOrder(Long productId) {
        return imageRepository.findByProductId(productId).stream()
                .map(ProductImage::getSortOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .map(n -> n + 1)
                .orElse(0);
    }

    private String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private ProductImageDTO toDTO(ProductImage i) {
        return ProductImageDTO.builder()
                .id(i.getId())
                .productId(i.getProductId())
                .url(i.getUrl())
                .altText(i.getAltText())
                .sortOrder(i.getSortOrder())
                .isPrimary(i.getIsPrimary())
                .build();
    }
}
