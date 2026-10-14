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
    private final SizeChartLibraryService sizeCharts;

    @Override
    public List<CategoryResponseDTO> getAllCategories() {
        return inTreeOrder(categoryRepository.findAll()).stream()
                .map(this::toEnrichedDTO)
                .collect(Collectors.toList());
    }

    /**
     * Categories as a tree read top to bottom: each one followed at once by
     * everything under it, siblings in the shop's order ({@link #SHOP_ORDER}). A category whose parent is not in
     * the list (deleted, or filtered out) is placed at the top level rather
     * than lost.
     */
    private static List<Category> inTreeOrder(List<Category> all) {
        java.util.Map<Long, Category> byId = new java.util.HashMap<>();
        for (Category c : all) if (c.getId() != null) byId.put(c.getId(), c);
        java.util.Comparator<Category> byName = SHOP_ORDER;
        java.util.Map<Long, List<Category>> children = new java.util.HashMap<>();
        List<Category> top = new java.util.ArrayList<>();
        for (Category c : all) {
            Long parentId = c.getParentId();
            if (parentId == null || !byId.containsKey(parentId) || parentId.equals(c.getId())) top.add(c);
            else children.computeIfAbsent(parentId, k -> new java.util.ArrayList<>()).add(c);
        }
        List<Category> ordered = new java.util.ArrayList<>();
        java.util.Set<Long> placed = new java.util.HashSet<>();
        java.util.Deque<Category> stack = new java.util.ArrayDeque<>();
        top.sort(byName.reversed());
        top.forEach(stack::push);
        while (!stack.isEmpty()) {
            Category c = stack.pop();
            if (c.getId() != null && !placed.add(c.getId())) continue;
            ordered.add(c);
            List<Category> under = children.getOrDefault(c.getId(), List.of());
            under.stream().sorted(byName.reversed()).forEach(stack::push);
        }
        // Anything caught in a loop was never reached from the top: keep it visible.
        for (Category c : all) if (c.getId() == null || placed.add(c.getId())) { if (!ordered.contains(c)) ordered.add(c); }
        return ordered;
    }

    /**
     * Siblings in the shop's order: the ones the admin arranged on "Shop Order"
     * first, in that order, then the rest by name.
     */
    public static final java.util.Comparator<Category> SHOP_ORDER = java.util.Comparator
            .comparing((Category c) -> c.getShopOrder() == null ? Integer.MAX_VALUE : c.getShopOrder())
            .thenComparing(c -> c.getName() == null ? "" : c.getName().toLowerCase(Locale.ROOT))
            .thenComparing(c -> c.getId() == null ? 0L : c.getId());

    /**
     * Puts siblings in the order given: the main categories when {@code parentId}
     * is null, otherwise the subcategories of that category. Every id must be a
     * live category with that parent. Siblings left out of the list are no longer
     * arranged, and follow the listed ones by name.
     */
    @Override
    @org.springframework.transaction.annotation.Transactional
    public List<CategoryResponseDTO> arrangeSiblings(Long parentId, List<Long> orderedIds) {
        List<Long> order = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(orderedIds == null ? List.<Long>of() : orderedIds));
        order.remove(null);
        List<Category> siblings = categoryRepository.findAll().stream()
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .filter(c -> java.util.Objects.equals(c.getParentId(), parentId))
                .toList();
        java.util.Map<Long, Category> byId = new java.util.HashMap<>();
        for (Category c : siblings) byId.put(c.getId(), c);
        for (Long id : order) {
            if (!byId.containsKey(id)) {
                throw new kn.org.deliverybackend.exception.InvalidRequestException(
                        "A category in the list is not in this group any more. Reload the page and try again.");
            }
        }
        for (Category c : siblings) {
            int at = order.indexOf(c.getId());
            Integer place = at < 0 ? null : at + 1;
            if (!java.util.Objects.equals(place, c.getShopOrder())) {
                c.setShopOrder(place);
                categoryRepository.save(c);
            }
        }
        return getAllCategories();
    }

    @Override
    public org.springframework.data.domain.Page<CategoryResponseDTO> adminPage(String q, int page, int size) {
        return adminPage(q, null, null, null, null, page, size);
    }

    @Override
    public org.springframework.data.domain.Page<CategoryResponseDTO> adminPage(String q, String status, String kind, String image,
                                                                               String sort, int page, int size) {
        // A shop keeps a handful of categories, so they are searched, narrowed
        // and ordered here and only the requested page leaves the server.
        String text = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        // Tree order first, then the search and filters: what matches keeps its place in the tree.
        List<Category> matching = inTreeOrder(categoryRepository.findAll(org.springframework.data.domain.Sort.by("id")).stream()
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted())).toList()).stream()
                .filter(c -> text.isEmpty() || contains(c.getName(), text) || contains(c.getSlug(), text))
                .filter(c -> switch (choice(status)) {
                    case "active" -> Boolean.TRUE.equals(c.getIsActive());
                    case "hidden" -> !Boolean.TRUE.equals(c.getIsActive());
                    default -> true;
                })
                .filter(c -> switch (choice(kind)) {
                    case "main" -> c.getParentId() == null;
                    case "sub" -> c.getParentId() != null;
                    default -> true;
                })
                .filter(c -> switch (choice(image)) {
                    case "missing" -> c.getImageUrl() == null || c.getImageUrl().isBlank();
                    case "with" -> c.getImageUrl() != null && !c.getImageUrl().isBlank();
                    default -> true;
                })
                .toList();
        int safeSize = kn.org.deliverybackend.util.SearchText.pageSize(size);
        int safePage = Math.max(page, 0);
        int from = Math.min(safePage * safeSize, matching.size());
        int to = Math.min(from + safeSize, matching.size());
        org.springframework.data.domain.PageRequest request = org.springframework.data.domain.PageRequest.of(safePage, safeSize);

        java.util.Comparator<Category> byName = java.util.Comparator.comparing(
                c -> c.getName() == null ? "" : c.getName().toLowerCase(java.util.Locale.ROOT));
        List<CategoryResponseDTO> content;
        switch (choice(sort)) {
            case "name" -> content = matching.stream().sorted(byName).toList().subList(from, to).stream().map(this::toEnrichedDTO).toList();
            case "name-desc" -> content = matching.stream().sorted(byName.reversed()).toList().subList(from, to).stream().map(this::toEnrichedDTO).toList();
            case "newest" -> content = matching.stream()
                    .sorted(java.util.Comparator.comparing(Category::getId, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                    .toList().subList(from, to).stream().map(this::toEnrichedDTO).toList();
            case "products", "products-asc" -> {
                // The product count is worked out per category, so every match is counted before ordering.
                java.util.Comparator<CategoryResponseDTO> byCount = java.util.Comparator.comparingLong(CategoryResponseDTO::getProductCount);
                if ("products".equals(choice(sort))) byCount = byCount.reversed();
                content = matching.stream().map(this::toEnrichedDTO)
                        .sorted(byCount.thenComparing(d -> d.getName() == null ? "" : d.getName().toLowerCase(java.util.Locale.ROOT)))
                        .toList().subList(from, to);
            }
            default -> content = matching.subList(from, to).stream().map(this::toEnrichedDTO).toList();
        }
        return new org.springframework.data.domain.PageImpl<>(content, request, matching.size());
    }

    private static String choice(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
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
        category.setSizeChartId(sizeCharts.checked(categoryRequestDTO.getSizeChartId()));
        applyHomeSectionFields(category, categoryRequestDTO, null);
        Category saved = categoryRepository.save(category);
        sizeCharts.setCategoryFitCharts(saved.getId(), categoryRequestDTO.getFitSizeChartIds());
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
        // Null leaves the chart as it is; 0 takes it away.
        if (categoryRequestDTO.getSizeChartId() != null) {
            category.setSizeChartId(sizeCharts.checked(categoryRequestDTO.getSizeChartId()));
        }
        applyHomeSectionFields(category, categoryRequestDTO, id);
        Category saved = categoryRepository.save(category);
        sizeCharts.setCategoryFitCharts(saved.getId(), categoryRequestDTO.getFitSizeChartIds());
        return toEnrichedDTO(saved);
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
     * The parent a category is being put under, checked: it has to exist, and
     * it can't be the category in hand or anything under it - that would make
     * a loop. Any depth is fine otherwise, and a category moves with
     * everything under it.
     */
    private Long checkedParent(Long parentId, Long selfId) {
        if (parentId == null) return null;
        if (parentId.equals(selfId)) {
            throw new kn.org.deliverybackend.exception.InvalidRequestException("A category can't be its own subcategory.");
        }
        Category parent = categoryRepository.findById(parentId)
                .filter(p -> !Boolean.TRUE.equals(p.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + parentId));
        if (selfId != null && kn.org.deliverybackend.service.CategoryTree.family(categoryRepository, selfId).contains(parentId)) {
            throw new kn.org.deliverybackend.exception.InvalidRequestException(
                    parent.getName() + " is inside this category, so this category can't be put under it.");
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
        // A category counts what is in everything under it too.
        java.util.Set<Long> family = kn.org.deliverybackend.service.CategoryTree.family(categoryRepository, category.getId());
        dto.setProductCount(productRepository.findByCategoryIdIn(family).size());
        dto.setOwnProductCount(family.size() == 1 ? dto.getProductCount()
                : productRepository.findByCategoryIdIn(java.util.Set.of(category.getId())).size());
        dto.setSubcategoryCount(family.size() - 1);
        kn.org.deliverybackend.service.CategoryTree.parentOf(categoryRepository, category)
                .ifPresent(parent -> dto.setParentName(parent.getName()));
        dto.setSizeChartId(category.getSizeChartId());
        dto.setFitSizeChartIds(sizeCharts.ownCategoryFitCharts(category.getId()));
        dto.setEffectiveSizeChartId(sizeCharts.forCategory(category));
        dto.setDepth(kn.org.deliverybackend.service.CategoryTree.depth(categoryRepository, category));
        dto.setPath(kn.org.deliverybackend.service.CategoryTree.path(categoryRepository, category));
        dto.setEffectiveShippingCharge(kn.org.deliverybackend.service.CategoryTree.shippingCharge(categoryRepository, category));
        dto.setEffectiveDiscountExcluded(kn.org.deliverybackend.service.CategoryTree.discountExcluded(categoryRepository, category));
        dto.setEffectiveShowStockQuantity(Boolean.TRUE.equals(
                kn.org.deliverybackend.service.CategoryTree.showStockQuantity(categoryRepository, category)));
        return dto;
    }
}
