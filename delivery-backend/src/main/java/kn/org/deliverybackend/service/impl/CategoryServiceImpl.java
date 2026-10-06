package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.category.CategoryRequestDTO;
import kn.org.deliverybackend.dto.response.category.CategoryResponseDTO;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.mapper.CategoryMapper;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final ProductRepository productRepository;

    @Override
    public List<CategoryResponseDTO> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::toEnrichedDTO)
                .collect(Collectors.toList());
    }

    @Override
    public org.springframework.data.domain.Page<CategoryResponseDTO> adminPage(String q, int page, int size) {
        // A shop keeps a handful of categories, so they are searched here and
        // only the requested page leaves the server.
        String text = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        List<Category> matching = categoryRepository.findAll(org.springframework.data.domain.Sort.by("id")).stream()
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .filter(c -> text.isEmpty() || contains(c.getName(), text) || contains(c.getSlug(), text))
                // Each main category, then its subcategories by name.
                .sorted(java.util.Comparator
                        .comparing((Category c) -> c.getParentId() != null ? c.getParentId() : c.getId())
                        .thenComparing(c -> c.getParentId() != null)
                        .thenComparing(c -> c.getName() == null ? "" : c.getName().toLowerCase(java.util.Locale.ROOT)))
                .toList();
        int safeSize = kn.org.deliverybackend.util.SearchText.pageSize(size);
        int safePage = Math.max(page, 0);
        int from = Math.min(safePage * safeSize, matching.size());
        int to = Math.min(from + safeSize, matching.size());
        List<CategoryResponseDTO> content = matching.subList(from, to).stream().map(this::toEnrichedDTO).toList();
        return new org.springframework.data.domain.PageImpl<>(content,
                org.springframework.data.domain.PageRequest.of(safePage, safeSize), matching.size());
    }

    private static boolean contains(String value, String lowerText) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(lowerText);
    }

    @Override
    public CategoryResponseDTO getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .map(this::toEnrichedDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
    }

    @Override
    public CategoryResponseDTO getCategoryBySlug(String slug) {
        return categoryRepository.findBySlugIgnoreCaseAndDeletedFalse(slug)
                .map(this::toEnrichedDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + slug));
    }

    @Override
    public CategoryResponseDTO createCategory(CategoryRequestDTO categoryRequestDTO) {
        Category category = categoryMapper.toEntity(categoryRequestDTO);
        category.setParentId(checkedParent(categoryRequestDTO.getParentId(), null));
        applyHomeSectionFields(category, categoryRequestDTO, null);
        Category saved = categoryRepository.save(category);
        return toEnrichedDTO(saved);
    }

    @Override
    public CategoryResponseDTO updateCategory(Long id, CategoryRequestDTO categoryRequestDTO) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
        category.setName(categoryRequestDTO.getName());
        category.setIsActive(categoryRequestDTO.getIsActive());
        category.setImageUrl(categoryRequestDTO.getImageUrl());
        category.setParentId(checkedParent(categoryRequestDTO.getParentId(), id));
        applyHomeSectionFields(category, categoryRequestDTO, id);
        return toEnrichedDTO(categoryRepository.save(category));
    }

    @Override
    public void deleteCategory(Long id) {
        int subcategories = categoryRepository.findByParentIdAndDeletedFalse(id).size();
        if (subcategories > 0) {
            throw new kn.org.deliverybackend.exception.InvalidRequestException(
                    "This category has " + subcategories + (subcategories == 1 ? " subcategory" : " subcategories")
                            + ". Delete or move " + (subcategories == 1 ? "it" : "them") + " first.");
        }
        categoryRepository.deleteById(id);
    }

    /**
     * The parent a category is being put under, checked: it has to exist, be a
     * main category itself, and not be the category in hand; and a category
     * that has subcategories can't become one. Two levels, no more.
     */
    private Long checkedParent(Long parentId, Long selfId) {
        if (parentId == null) return null;
        if (parentId.equals(selfId)) {
            throw new kn.org.deliverybackend.exception.InvalidRequestException("A category can't be its own subcategory.");
        }
        Category parent = categoryRepository.findById(parentId)
                .filter(p -> !Boolean.TRUE.equals(p.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + parentId));
        if (parent.getParentId() != null) {
            throw new kn.org.deliverybackend.exception.InvalidRequestException(
                    parent.getName() + " is a subcategory itself. Choose a main category.");
        }
        if (selfId != null && !categoryRepository.findByParentIdAndDeletedFalse(selfId).isEmpty()) {
            throw new kn.org.deliverybackend.exception.InvalidRequestException(
                    "This category has subcategories, so it can't become one. Move them first.");
        }
        return parentId;
    }

    /**
     * Fills in the landing-page/section fields, deriving a slug when the admin
     * left it blank and guaranteeing it is unique.
     */
    private void applyHomeSectionFields(Category category, CategoryRequestDTO dto, Long selfId) {
        // The landing page gives sections to main categories only.
        category.setShowOnHome(category.getParentId() == null && Boolean.TRUE.equals(dto.getShowOnHome()));
        category.setHomeSortOrder(dto.getHomeSortOrder() != null ? dto.getHomeSortOrder() : 0);
        category.setDiscountExcluded(Boolean.TRUE.equals(dto.getDiscountExcluded()));
        // Null leaves it as it is, so a save that doesn't know the switch can't turn it off.
        if (dto.getShowStockQuantity() != null) category.setShowStockQuantity(dto.getShowStockQuantity());

        String requested = dto.getSlug() != null && !dto.getSlug().isBlank()
                ? dto.getSlug()
                : dto.getName();
        category.setSlug(uniqueSlug(slugify(requested), selfId));
    }

    /**
     * Lowercases, replaces every run of non-alphanumerics with a single hyphen
     * and trims stray hyphens, so "Erezer Pink!" becomes "erezer-pink".
     */
    private String slugify(String raw) {
        if (raw == null) return null;
        String slug = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        return slug.isBlank() ? null : slug.substring(0, Math.min(slug.length(), 140));
    }

    /**
     * A slug addresses a page, so a clash would make one category unreachable.
     * Appends -2, -3 ... until free rather than rejecting the save, which would
     * be a confusing failure for an admin simply reusing a common word.
     */
    private String uniqueSlug(String base, Long selfId) {
        if (base == null) return null;
        String candidate = base;
        for (int suffix = 2; isSlugTaken(candidate, selfId); suffix++) {
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

    private boolean isSlugTaken(String slug, Long selfId) {
        return selfId == null
                ? categoryRepository.findBySlugIgnoreCaseAndDeletedFalse(slug).isPresent()
                : categoryRepository.findBySlugIgnoreCaseAndDeletedFalseAndIdNot(slug, selfId).isPresent();
    }

    private CategoryResponseDTO toEnrichedDTO(Category category) {
        CategoryResponseDTO dto = categoryMapper.toResponseDTO(category);
        // A main category counts what is in its subcategories too.
        java.util.Set<Long> family = kn.org.deliverybackend.service.CategoryTree.family(categoryRepository, category.getId());
        dto.setProductCount(productRepository.findByCategoryIdIn(family).size());
        dto.setOwnProductCount(family.size() == 1 ? dto.getProductCount()
                : productRepository.findByCategoryIdIn(java.util.Set.of(category.getId())).size());
        dto.setSubcategoryCount(family.size() - 1);
        kn.org.deliverybackend.service.CategoryTree.parentOf(categoryRepository, category)
                .ifPresent(parent -> dto.setParentName(parent.getName()));
        dto.setEffectiveShippingCharge(kn.org.deliverybackend.service.CategoryTree.shippingCharge(categoryRepository, category));
        dto.setEffectiveDiscountExcluded(kn.org.deliverybackend.service.CategoryTree.discountExcluded(categoryRepository, category));
        dto.setEffectiveShowStockQuantity(Boolean.TRUE.equals(
                kn.org.deliverybackend.service.CategoryTree.showStockQuantity(categoryRepository, category)));
        return dto;
    }
}
