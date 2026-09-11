package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.request.category.CategoryRequestDTO;
import kn.org.deliverybackend.dto.response.category.CategoryResponseDTO;

import java.util.List;

public interface CategoryService {
    List<CategoryResponseDTO> getAllCategories();
    CategoryResponseDTO getCategoryById(Long id);

    /** The admin Categories page: one page, optionally searched by name or web address (slug), in id order. */
    org.springframework.data.domain.Page<CategoryResponseDTO> adminPage(String q, int page, int size);

    /** Resolves a storefront URL segment, e.g. "erezer-pink", to its category. */
    CategoryResponseDTO getCategoryBySlug(String slug);
    CategoryResponseDTO createCategory(CategoryRequestDTO categoryRequestDTO);
    CategoryResponseDTO updateCategory(Long id, CategoryRequestDTO categoryRequestDTO);
    void deleteCategory(Long id);
}
