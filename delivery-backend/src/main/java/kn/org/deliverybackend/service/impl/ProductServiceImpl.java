package kn.org.deliverybackend.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import kn.org.deliverybackend.dto.request.product.ProductBrowseFilter;
import kn.org.deliverybackend.dto.request.product.ProductRequestDTO;
import kn.org.deliverybackend.dto.response.product.ProductFacetsDTO;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.entity.Inventory;
import kn.org.deliverybackend.entity.Product;

import kn.org.deliverybackend.mapper.ProductMapper;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.InventoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.service.FileStorageService;
import kn.org.deliverybackend.service.InventoryService;
import kn.org.deliverybackend.service.ProductPricing;
import kn.org.deliverybackend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import kn.org.deliverybackend.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    /** Largest page the shop list will serve, whatever is asked for. */
    private static final int MAX_BROWSE_PAGE_SIZE = 60;

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final FileStorageService fileStorageService;
    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;

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

    // ── shop list ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> browse(ProductBrowseFilter filter, String sort, int page, int size) {
        Specification<Product> spec = (root, query, cb) -> {
            // Order only the page query. Spring reuses this for the count
            // query, which must stay unordered.
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                query.orderBy(browseOrder(root, cb, sort));
            }
            return cb.and(browsePredicates(root, cb, filter).toArray(Predicate[]::new));
        };
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_BROWSE_PAGE_SIZE));
        return productRepository.findAll(spec, request).map(this::toEnrichedResponseDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductFacetsDTO facets(String q, Long categoryId) {
        ProductBrowseFilter scope = new ProductBrowseFilter(q, categoryId, null, null, null);
        return new ProductFacetsDTO(distinctValues("gender", scope), distinctValues("brand", scope),
                highestShownPrice(scope));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> adminSearch(String q, Long categoryId, int page, int size) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isFalse(cb.coalesce(root.<Boolean>get("deleted"), Boolean.FALSE)));
            String text = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
            if (!text.isEmpty()) {
                String pattern = likePattern(text);
                // Products whose category's name matches, e.g. "hood" finds everything in Hoodies.
                jakarta.persistence.criteria.Subquery<Long> byCategory = query.subquery(Long.class);
                Root<kn.org.deliverybackend.entity.Category> category =
                        byCategory.from(kn.org.deliverybackend.entity.Category.class);
                byCategory.select(category.get("id")).where(cb.like(cb.lower(category.get("name")), pattern, '\\'));
                where.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("sku")), pattern, '\\'),
                        cb.like(cb.lower(root.get("productCode")), pattern, '\\'),
                        cb.like(cb.lower(root.get("brand")), pattern, '\\'),
                        root.get("categoryId").in(byCategory)));
            }
            if (categoryId != null) where.add(cb.equal(root.get("categoryId"), categoryId));
            return cb.and(where.toArray(Predicate[]::new));
        };
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_BROWSE_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));
        return productRepository.findAll(spec, request).map(this::toEnrichedResponseDTO);
    }

    /** Never deleted products; search in name, brand or description; then each filter that is set. */
    private static List<Predicate> browsePredicates(Root<Product> root, CriteriaBuilder cb, ProductBrowseFilter f) {
        List<Predicate> where = new ArrayList<>();
        where.add(cb.isFalse(cb.coalesce(root.<Boolean>get("deleted"), Boolean.FALSE)));
        String q = f.q() == null ? "" : f.q().trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            String pattern = likePattern(q);
            // The product code too: the admin product pickers use this search, and a
            // customer who knows a code (say from a tag) can find the product.
            where.add(cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, '\\'),
                    cb.like(cb.lower(root.get("brand")), pattern, '\\'),
                    cb.like(cb.lower(root.get("description")), pattern, '\\'),
                    cb.like(cb.lower(root.get("productCode")), pattern, '\\')));
        }
        if (f.categoryId() != null) where.add(cb.equal(root.get("categoryId"), f.categoryId()));
        if (f.gender() != null && !f.gender().isBlank()) where.add(cb.equal(root.get("gender"), f.gender().trim()));
        if (f.brand() != null && !f.brand().isBlank()) where.add(cb.equal(root.get("brand"), f.brand().trim()));
        if (f.maxPrice() != null) where.add(cb.le(shownPrice(root, cb), f.maxPrice()));
        return where;
    }

    /**
     * Featured first and then newest, or by the price customers see. Always
     * ends with the id, so a page never repeats or skips a product.
     */
    private static List<jakarta.persistence.criteria.Order> browseOrder(Root<Product> root, CriteriaBuilder cb, String sort) {
        List<jakarta.persistence.criteria.Order> order = new ArrayList<>();
        switch (sort == null ? "featured" : sort) {
            case "price-asc" -> {
                order.add(cb.asc(shownPrice(root, cb)));
                order.add(cb.asc(root.get("id")));
            }
            case "price-desc" -> {
                order.add(cb.desc(shownPrice(root, cb)));
                order.add(cb.asc(root.get("id")));
            }
            default -> {
                order.add(cb.desc(cb.coalesce(root.<Boolean>get("isFeatured"), Boolean.FALSE)));
                order.add(cb.desc(root.get("id")));
            }
        }
        return order;
    }

    /** The price customers see: the sale price when there is one. */
    private static Expression<BigDecimal> shownPrice(Root<Product> root, CriteriaBuilder cb) {
        return cb.coalesce(root.<BigDecimal>get("discountPrice"), root.<BigDecimal>get("price"));
    }

    /** "%text%", with the search's own % and _ taken literally. */
    private static String likePattern(String text) {
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private List<String> distinctValues(String field, ProductBrowseFilter scope) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<String> query = cb.createQuery(String.class);
        Root<Product> root = query.from(Product.class);
        Expression<String> value = root.get(field);
        List<Predicate> where = browsePredicates(root, cb, scope);
        where.add(cb.isNotNull(value));
        where.add(cb.notEqual(cb.trim(value), ""));
        query.select(value).distinct(true).where(where.toArray(Predicate[]::new)).orderBy(cb.asc(value));
        return entityManager.createQuery(query).getResultList();
    }

    private BigDecimal highestShownPrice(ProductBrowseFilter scope) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> query = cb.createQuery(BigDecimal.class);
        Root<Product> root = query.from(Product.class);
        query.select(cb.max(shownPrice(root, cb))).where(browsePredicates(root, cb, scope).toArray(Predicate[]::new));
        return entityManager.createQuery(query).getSingleResult();
    }

    // ── single products ─────────────────────────────────────────────────────

    @Override
    public ProductResponseDTO getProductById(Long id) {
        return productRepository.findById(id)
                .map(this::toEnrichedResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
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
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (image != null && !image.isEmpty()) {
            String imageUrl = fileStorageService.uploadFile(image);
            product.setImageUrl(imageUrl);
        }

        product.setCategoryId(productRequestDTO.getCategoryId());
        product.setName(productRequestDTO.getName());
        product.setProductCode(productRequestDTO.getProductCode());
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

        // Exclude from automatic discounts (null leaves the existing value untouched).
        if (productRequestDTO.getDiscountExcluded() != null) {
            product.setDiscountExcluded(productRequestDTO.getDiscountExcluded());
        }

        return toEnrichedResponseDTO(productRepository.save(product));
    }

    private ProductResponseDTO toEnrichedResponseDTO(Product product) {
        ProductResponseDTO dto = productMapper.toResponseDTO(product);
        dto.setStockStatus(inventoryService.computeStatus(product));
        dto.setSku(product.getSku());
        dto.setUnit(product.getUnit());
        // Enrich with the category's name and its discount exclusion, so a
        // storefront card knows the product is at full price without having to
        // fetch the category separately.
        dto.setCategoryDiscountExcluded(false);
        if (product.getCategoryId() != null) {
            categoryRepository.findById(product.getCategoryId()).ifPresent(cat -> {
                dto.setCategoryName(cat.getName());
                dto.setCategoryDiscountExcluded(Boolean.TRUE.equals(cat.getDiscountExcluded()));
            });
        }
        return dto;
    }

    private void calculateAndSetDiscountPrice(Product product, ProductRequestDTO dto) {
        product.setDiscountPrice(ProductPricing.salePrice(dto.getPrice(), dto.getDiscountPercentage()));
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
