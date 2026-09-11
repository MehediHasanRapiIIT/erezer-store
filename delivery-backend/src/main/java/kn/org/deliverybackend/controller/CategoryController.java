package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.access.RequiresPermission;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.request.category.CategoryRequestDTO;
import kn.org.deliverybackend.dto.response.category.CategoryResponseDTO;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.service.CategoryService;
import kn.org.deliverybackend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@CrossOrigin("*")
public class CategoryController {

    private final CategoryService categoryService;
    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }
    /**
     * Resolves a storefront collection URL to its category, e.g. GET
     * /api/categories/slug/erezer-pink backing the /erezer-pink page.
     */
    @GetMapping("/slug/{slug}")
    public ResponseEntity<CategoryResponseDTO> getCategoryBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(categoryService.getCategoryBySlug(slug));
    }


    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponseDTO> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @RequiresPermission(Perm.CATEGORIES_CREATE)
    @PostMapping
    public ResponseEntity<CategoryResponseDTO> createCategory(@Valid @RequestBody CategoryRequestDTO categoryRequestDTO) {
        // "Never discount" changes what customers pay, so it needs the discount switches permission.
        if (Boolean.TRUE.equals(categoryRequestDTO.getDiscountExcluded())) {
            StaffAccess.require(Perm.DISCOUNTS_SWITCHES);
        }
        return ResponseEntity.ok(categoryService.createCategory(categoryRequestDTO));
    }

    @RequiresPermission(Perm.CATEGORIES_EDIT)
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponseDTO> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequestDTO categoryRequestDTO) {
        // The service stores a missing "Never discount" as off, so compare what
        // will be stored, not only what was sent: leaving the field out must
        // not be a way round the permission.
        boolean excludedAfter = Boolean.TRUE.equals(categoryRequestDTO.getDiscountExcluded());
        if (excludedAfter != Boolean.TRUE.equals(categoryService.getCategoryById(id).getDiscountExcluded())) {
            StaffAccess.require(Perm.DISCOUNTS_SWITCHES);
        }
        return ResponseEntity.ok(categoryService.updateCategory(id, categoryRequestDTO));
    }

    @RequiresPermission(Perm.CATEGORIES_DELETE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{categoryId}/products")
    public ResponseEntity<List<ProductResponseDTO>> getProductsByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(productService.getProductsByCategory(categoryId));
    }
}
