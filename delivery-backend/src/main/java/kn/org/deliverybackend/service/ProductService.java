package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.request.product.ProductRequestDTO;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductService {
    List<ProductResponseDTO> searchProducts(String name);
    List<ProductResponseDTO> getProductsByCategory(Long categoryId);
    List<ProductResponseDTO> getAllProducts();
    Page<ProductResponseDTO> getProductsPaged(int page, int size);
    ProductResponseDTO getProductById(Long id);

    /** Enriched DTOs for the given ids, preserving order and skipping any not found. */
    List<ProductResponseDTO> getProductsByIds(List<Long> ids);
    ProductResponseDTO createProduct(ProductRequestDTO productRequestDTO, MultipartFile image);
    ProductResponseDTO updateProduct(Long id, ProductRequestDTO productRequestDTO, MultipartFile image);

    /** Toggle the "Featured products" home flag only (leaves pricing/stock untouched). */
    ProductResponseDTO setFeatured(Long id, boolean value);

    void deleteProduct(Long id);

    /** Up to {@code limit} products in the same category as {@code id}, excluding the caller. */
    List<ProductResponseDTO> getRelatedProducts(Long id, int limit);
}
