package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.product.ProductRequestDTO;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.entity.Inventory;
import kn.org.deliverybackend.entity.Product;

import kn.org.deliverybackend.mapper.ProductMapper;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.InventoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.service.FileStorageService;
import kn.org.deliverybackend.service.InventoryService;
import kn.org.deliverybackend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import kn.org.deliverybackend.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final FileStorageService fileStorageService;
    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public List<ProductResponseDTO> searchProducts(String name) {
        return productRepository.findByNameContainingIgnoreCase(name).stream()
                .map(this::toEnrichedResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductResponseDTO> getProductsByCategory(Long categoryId) {
        return productRepository.findByCategoryId(categoryId).stream()
                .map(this::toEnrichedResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductResponseDTO> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::toEnrichedResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ProductResponseDTO> getProductsPaged(int page, int size) {
        return productRepository.findAll(PageRequest.of(page, size, Sort.by("id").descending()))
                .map(this::toEnrichedResponseDTO);
    }

    @Override
    public ProductResponseDTO getProductById(Long id) {
        return productRepository.findById(id)
                .map(this::toEnrichedResponseDTO)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
    }

    @Override
    public List<ProductResponseDTO> getProductsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        // Resolve to entities, then re-emit in the caller's order, skipping missing ids.
        Map<Long, Product> byId = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        return ids.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toEnrichedResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ProductResponseDTO createProduct(ProductRequestDTO productRequestDTO, MultipartFile image) {
        Product product = productMapper.toEntity(productRequestDTO);

        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.uploadFile(image);
            product.setImageUrl(imageUrl);
        }

        calculateAndSetDiscountPrice(product, productRequestDTO);
        if (productRequestDTO.getLowStockThreshold() != null) {
            product.setLowStockThreshold(productRequestDTO.getLowStockThreshold());
        }
        Product saved = productRepository.save(product);

        // Auto-create inventory row for the new product (starts at 0 stock)
        Inventory inventory = new Inventory();
        inventory.setProductId(saved.getId());
        inventory.setStockQuantity(0);
        inventory.setUnit(productRequestDTO.getUnit());
        inventoryRepository.save(inventory);

        return toEnrichedResponseDTO(saved);
    }

    @Override
    public ProductResponseDTO updateProduct(Long id, ProductRequestDTO productRequestDTO, MultipartFile image) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.uploadFile(image);
            product.setImageUrl(imageUrl);
        }

        product.setCategoryId(productRequestDTO.getCategoryId());
        product.setName(productRequestDTO.getName());
        product.setDescription(productRequestDTO.getDescription());
        product.setPrice(productRequestDTO.getPrice());
        calculateAndSetDiscountPrice(product, productRequestDTO);
        product.setShopId(productRequestDTO.getShopId());
        if (productRequestDTO.getImageUrl() != null && !productRequestDTO.getImageUrl().isBlank()) {
            product.setImageUrl(productRequestDTO.getImageUrl());
        }
        product.setIsAvailable(productRequestDTO.getIsAvailable());
        // Home-section flags (null leaves the existing value untouched).
        if (productRequestDTO.getIsNewArrival() != null) product.setIsNewArrival(productRequestDTO.getIsNewArrival());
        if (productRequestDTO.getIsFeatured() != null)   product.setIsFeatured(productRequestDTO.getIsFeatured());
        if (productRequestDTO.getUnit() != null) product.setUnit(productRequestDTO.getUnit());
        if (productRequestDTO.getLowStockThreshold() != null) {
            product.setLowStockThreshold(productRequestDTO.getLowStockThreshold());
        }
        // Clothing attributes (null leaves the existing value untouched).
        if (productRequestDTO.getBrand() != null)            product.setBrand(productRequestDTO.getBrand());
        if (productRequestDTO.getGender() != null)           product.setGender(productRequestDTO.getGender());
        if (productRequestDTO.getMaterial() != null)         product.setMaterial(productRequestDTO.getMaterial());
        if (productRequestDTO.getCareInstructions() != null) product.setCareInstructions(productRequestDTO.getCareInstructions());

        // Custom sizing (null leaves the existing value untouched).
        if (productRequestDTO.getCustomSizeEnabled() != null)   product.setCustomSizeEnabled(productRequestDTO.getCustomSizeEnabled());
        if (productRequestDTO.getCustomSizeSurcharge() != null) product.setCustomSizeSurcharge(productRequestDTO.getCustomSizeSurcharge());
        if (productRequestDTO.getCustomSizeNote() != null)      product.setCustomSizeNote(productRequestDTO.getCustomSizeNote());

        return toEnrichedResponseDTO(productRepository.save(product));
    }

    private ProductResponseDTO toEnrichedResponseDTO(Product product) {
        ProductResponseDTO dto = productMapper.toResponseDTO(product);
        dto.setStockStatus(inventoryService.computeStatus(product));
        dto.setSku(product.getSku());
        dto.setUnit(product.getUnit());
        // Enrich with category name
        if (product.getCategoryId() != null) {
            categoryRepository.findById(product.getCategoryId())
                    .ifPresent(cat -> dto.setCategoryName(cat.getName()));
        }
        return dto;
    }

    private void calculateAndSetDiscountPrice(Product product, ProductRequestDTO dto) {
        if (dto.getDiscountPercentage() != null && dto.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discountAmount = dto.getPrice()
                    .multiply(dto.getDiscountPercentage())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            product.setDiscountPrice(dto.getPrice().subtract(discountAmount));
        } else {
            product.setDiscountPrice(dto.getPrice());
        }
    }

    @Override
    public ProductResponseDTO setFeatured(Long id, boolean value) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        product.setIsFeatured(value);
        return toEnrichedResponseDTO(productRepository.save(product));
    }

    @Override
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getRelatedProducts(Long id, int limit) {
        int capped = Math.min(Math.max(limit, 1), 20);
        Product anchor = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        if (anchor.getCategoryId() == null) {
            return List.of();
        }
        return productRepository.findRelated(
                        anchor.getCategoryId(), id, PageRequest.of(0, capped))
                .stream()
                .map(this::toEnrichedResponseDTO)
                .collect(Collectors.toList());
    }
}
