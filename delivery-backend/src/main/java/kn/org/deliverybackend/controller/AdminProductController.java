package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.dto.request.product.ProductBatchRequestDTO;
import kn.org.deliverybackend.dto.request.product.ProductRequestDTO;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.service.ProductCreationService;
import kn.org.deliverybackend.service.ProductService;
import kn.org.deliverybackend.service.impl.ProductBatchService;
import kn.org.deliverybackend.service.impl.ProductCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.BeanUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Admin-only product endpoints that don't fit the public catalogue API: the
 * searchable product list and quick flag toggles. Keycloak-protected (chain 1).
 */
@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AdminProductController {

    private final ProductService productService;
    private final ProductCreationService productCreationService;
    private final ProductBatchService productBatchService;
    private final ProductCodes productCodes;
    private final Validator validator;

    /**
     * "Add product" in one click: the product, its pictures and its sizes, saved
     * together or not at all.
     *
     * <p>Asks for the same permissions as the separate screens would: adding
     * pictures needs "Manage photos", sizes need "Manage sizes and colours", and a
     * size's stock or own price needs "Change stock" or "Change prices".
     */
    @RequiresPermission({Perm.PRODUCTS_CREATE, Perm.PRODUCTS_PRICE})
    @PostMapping(value = "/full", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponseDTO> createWithEverything(
            @Valid @RequestPart("product") ProductRequestDTO product,
            @RequestPart(value = "sizes", required = false) List<VariantRequestDTO> sizes,
            @RequestPart(value = "pictures", required = false) List<MultipartFile> pictures) {
        List<VariantRequestDTO> rows = sizes == null ? List.of() : sizes;
        List<MultipartFile> photos = pictures == null ? List.of()
                : pictures.stream().filter(f -> f != null && !f.isEmpty()).toList();

        ProductAccess.checkProductFields(product, null);
        if (!photos.isEmpty()) {
            StaffAccess.require(Perm.PRODUCTS_IMAGES);
        }
        // Options decide which variants a product has, like its sizes do.
        if (product.getOptions() != null && !product.getOptions().isEmpty()) {
            StaffAccess.require(Perm.PRODUCTS_VARIANTS);
        }
        checkSizes(rows);

        ProductResponseDTO created = productCreationService.createWithEverything(product, rows, photos);
        StaffAccess.describe("Added product " + created.getName() + " with "
                + photos.size() + (photos.size() == 1 ? " picture" : " pictures") + " and "
                + rows.size() + (rows.size() == 1 ? " size" : " sizes"));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * The next product codes for a category: its initials and the next free
     * numbers, e.g. EP-1003, EP-1004. A suggestion only; staff may type any code.
     */
    @RequiresPermission(value = {Perm.PRODUCTS_CREATE, Perm.PRODUCTS_EDIT}, mode = RequiresPermission.Mode.ANY)
    @GetMapping("/next-codes")
    public ResponseEntity<List<String>> nextCodes(@RequestParam Long categoryId,
                                                  @RequestParam(defaultValue = "1") int count) {
        return ResponseEntity.ok(productCodes.next(categoryId, count));
    }

    /**
     * "Add several products": rows that share a category, description, price,
     * discount and sizes, each with its own name, code and pictures. Saved all
     * together or not at all. Row {@code i}'s pictures are the parts named
     * {@code pictures-i}.
     */
    @RequiresPermission({Perm.PRODUCTS_CREATE, Perm.PRODUCTS_PRICE})
    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ProductResponseDTO>> createBatch(
            @RequestPart("batch") ProductBatchRequestDTO batch,
            MultipartHttpServletRequest request) {
        List<ProductBatchRequestDTO.Item> items = batch.getItems() == null ? List.of() : batch.getItems();
        ProductRequestDTO shared = batch.getShared() == null ? new ProductRequestDTO() : batch.getShared();
        List<VariantRequestDTO> sizes = batch.getSizes() == null ? List.of() : batch.getSizes();

        // Each row becomes an ordinary product request and obeys the same rules.
        List<ProductRequestDTO> products = new ArrayList<>(items.size());
        List<List<MultipartFile>> pictures = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            ProductBatchRequestDTO.Item item = items.get(i);
            ProductRequestDTO product = new ProductRequestDTO();
            BeanUtils.copyProperties(shared, product);
            product.setName(item.getName() == null ? null : item.getName().trim());
            product.setProductCode(item.getProductCode() == null ? null : item.getProductCode().trim());
            if (item.getPrice() != null) {
                product.setPrice(item.getPrice());
            }
            if (item.getSizeChartId() != null) {
                product.setSizeChartId(item.getSizeChartId());
            }
            Set<ConstraintViolation<ProductRequestDTO>> problems = validator.validate(product);
            if (!problems.isEmpty()) {
                throw new InvalidRequestException("Row " + (i + 1) + ": " + problems.iterator().next().getMessage());
            }
            ProductAccess.checkProductFields(product, null);
            products.add(product);
            pictures.add(request.getFiles("pictures-" + i).stream().filter(f -> !f.isEmpty()).toList());
        }
        if (pictures.stream().anyMatch(list -> !list.isEmpty())) {
            StaffAccess.require(Perm.PRODUCTS_IMAGES);
        }
        if (shared.getOptions() != null && !shared.getOptions().isEmpty()) {
            StaffAccess.require(Perm.PRODUCTS_VARIANTS);
        }
        checkSizes(sizes);

        List<ProductResponseDTO> created = productBatchService.createAll(products, sizes, pictures);
        int pictureCount = pictures.stream().mapToInt(List::size).sum();
        StaffAccess.describe("Added " + created.size() + (created.size() == 1 ? " product" : " products")
                + (created.isEmpty() ? "" : " (" + created.get(0).getProductCode()
                    + (created.size() > 1 ? " to " + created.get(created.size() - 1).getProductCode() : "") + ")")
                + " with " + pictureCount + (pictureCount == 1 ? " picture" : " pictures"));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Sizes sent with new products: each must pass the rules a single size does
     * (a list inside a multipart request isn't checked by @Valid), and setting
     * them needs the permissions the sizes list would ask for.
     */
    private void checkSizes(List<VariantRequestDTO> rows) {
        if (rows.isEmpty()) return;
        StaffAccess.require(Perm.PRODUCTS_VARIANTS);
        for (VariantRequestDTO row : rows) {
            Set<ConstraintViolation<VariantRequestDTO>> problems = validator.validate(row);
            if (!problems.isEmpty()) {
                throw new InvalidRequestException("Size " + (row.getSize() == null ? "" : row.getSize())
                        + ": " + problems.iterator().next().getMessage());
            }
            ProductAccess.checkVariantFields(row, null);
        }
    }

    /**
     * The Products page: {@code q} searches name, SKU, brand and category name
     * across all products, {@code categoryId} keeps one category, newest first.
     */
    @RequiresPermission(Perm.PRODUCTS_VIEW)
    @GetMapping
    public Page<ProductResponseDTO> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return productService.adminSearch(q, categoryId, page, size);
    }

    /** Toggle the "Featured products" home-page flag without touching pricing/stock. */
    @RequiresPermission(Perm.PRODUCTS_FEATURE)
    @PatchMapping("/{id}/featured")
    public ResponseEntity<ProductResponseDTO> setFeatured(
            @PathVariable Long id,
            @RequestParam("value") boolean value) {
        return ResponseEntity.ok(productService.setFeatured(id, value));
    }

    /**
     * The "Show qty" switch in the products list: the product page shows the stock
     * quantity (QUANTITY) or labels (LABEL), or follows its category (CATEGORY).
     */
    @RequiresPermission(Perm.PRODUCTS_EDIT)
    @PatchMapping("/{id}/stock-display")
    public ResponseEntity<ProductResponseDTO> setStockDisplay(
            @PathVariable Long id,
            @RequestParam("value") kn.org.deliverybackend.enumeration.StockDisplay value) {
        ProductResponseDTO result = productService.setStockDisplay(id, value);
        kn.org.deliverybackend.access.StaffAccess.describe(switch (value) {
            case QUANTITY -> "Showed the stock quantity for " + result.getName();
            case LABEL -> "Showed stock labels for " + result.getName();
            case CATEGORY -> "Made " + result.getName() + " follow its category for stock";
        });
        return ResponseEntity.ok(result);
    }
}
