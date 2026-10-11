package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.request.category.CategoryRequestDTO;
import kn.org.deliverybackend.dto.response.category.CategoryResponseDTO;

import java.util.List;

public interface CategoryService {
    List<CategoryResponseDTO> getAllCategories();
    CategoryResponseDTO getCategoryById(Long id);

    /** The admin Categories page: one page, optionally searched by name or web address (slug), in id order. */
    org.springframework.data.domain.Page<CategoryResponseDTO> adminPage(String q, int page, int size);

    /** Puts the main categories (parentId null) or one category's subcategories in the order given; answers with every category. */
    List<CategoryResponseDTO> arrangeSiblings(Long parentId, List<Long> orderedIds);

    /**
     * One page of the admin Categories list, narrowed and ordered.
     *
     * @param status "active", "hidden", or anything else for both
     * @param kind   "main", "sub", or anything else for both
     * @param image  "missing" for categories with no picture, "with" for those that have one, anything else for both
     * @param sort   "name", "name-desc", "products", "products-asc", "newest"; anything else keeps the tree order
     */
    org.springframework.data.domain.Page<CategoryResponseDTO> adminPage(String q, String status, String kind, String image,
                                                                        String sort, int page, int size);

    /** Resolves a storefront URL segment, e.g. "erezer-pink", to its category. */
    CategoryResponseDTO getCategoryBySlug(String slug);
    CategoryResponseDTO createCategory(CategoryRequestDTO categoryRequestDTO);
    CategoryResponseDTO updateCategory(Long id, CategoryRequestDTO categoryRequestDTO);
    void deleteCategory(Long id);
}
