package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.request.category.CategoryRequestDTO;
import kn.org.deliverybackend.dto.response.category.CategoryResponseDTO;

import java.util.List;

public interface CategoryService {
    List<CategoryResponseDTO> getAllCategories();
    CategoryResponseDTO getCategoryById(Long id);

    /** Resolves a storefront URL segment, e.g. "erezer-pink", to its category. */
    CategoryResponseDTO getCategoryBySlug(String slug);
    CategoryResponseDTO createCategory(CategoryRequestDTO categoryRequestDTO);
    CategoryResponseDTO updateCategory(Long id, CategoryRequestDTO categoryRequestDTO);
    void deleteCategory(Long id);
}
