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
    public CategoryResponseDTO getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .map(this::toEnrichedDTO)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
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
        applyHomeSectionFields(category, categoryRequestDTO, null);
        Category saved = categoryRepository.save(category);
        return toEnrichedDTO(saved);
    }

    @Override
    public CategoryResponseDTO updateCategory(Long id, CategoryRequestDTO categoryRequestDTO) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
        category.setName(categoryRequestDTO.getName());
        category.setIsActive(categoryRequestDTO.getIsActive());
        category.setImageUrl(categoryRequestDTO.getImageUrl());
        applyHomeSectionFields(category, categoryRequestDTO, id);
        return toEnrichedDTO(categoryRepository.save(category));
    }

    @Override
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }

    /**
     * Fills in the landing-page/section fields, deriving a slug when the admin
     * left it blank and guaranteeing it is unique.
     */
    private void applyHomeSectionFields(Category category, CategoryRequestDTO dto, Long selfId) {
        category.setShowOnHome(Boolean.TRUE.equals(dto.getShowOnHome()));
        category.setHomeSortOrder(dto.getHomeSortOrder() != null ? dto.getHomeSortOrder() : 0);
        category.setDiscountExcluded(Boolean.TRUE.equals(dto.getDiscountExcluded()));

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
        long count = productRepository.findByCategoryId(category.getId()).size();
        dto.setProductCount(count);
        return dto;
    }
}
