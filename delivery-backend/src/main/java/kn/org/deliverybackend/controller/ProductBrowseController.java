package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.dto.request.product.ProductBrowseFilter;
import kn.org.deliverybackend.dto.response.product.ProductFacetsDTO;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * The shop's product list. Search, filters and sort are done here and one
 * page comes back with the total, so the shop never downloads the whole
 * catalogue. Public, like every product read.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products: shop list")
public class ProductBrowseController {

    private final ProductService productService;

    /**
     * {@code sort}: {@code featured} (featured first, then newest; the default),
     * {@code price-asc} or {@code price-desc}, both on the price customers see.
     */
    @GetMapping("/browse")
    public Page<ProductResponseDTO> browse(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "featured") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return productService.browse(new ProductBrowseFilter(q, categoryId, gender, brand, maxPrice), sort, page, size);
    }

    /** The genders, brands and highest price available for this search and category. */
    @GetMapping("/facets")
    public ProductFacetsDTO facets(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) Long categoryId) {
        return productService.facets(q, categoryId);
    }
}
