package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.product.ProductRequestDTO;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.service.ProductCreationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * "Add several products": each row becomes a product of its own, with its own
 * pictures, and the lot is saved in one transaction — all of them, or none.
 *
 * <p>Every row is checked before the first is saved, so a bad picture in the
 * last row costs nothing; the message names the row. Each product is then
 * saved by {@link ProductCreationService}, which joins this transaction and
 * removes its stored pictures again if the batch does not go through.
 */
@Service
@RequiredArgsConstructor
public class ProductBatchService {

    /** A batch is a delivery or a photo shoot, not the whole catalogue. */
    static final int MAX_PRODUCTS = 20;

    private final ProductCreationService creation;

    @Transactional
    public List<ProductResponseDTO> createAll(List<ProductRequestDTO> products,
                                              List<VariantRequestDTO> sizes,
                                              List<List<MultipartFile>> picturesPerProduct) {
        if (products == null || products.isEmpty()) {
            throw new InvalidRequestException("Add at least one product.");
        }
        if (products.size() > MAX_PRODUCTS) {
            throw new InvalidRequestException("Up to " + MAX_PRODUCTS + " products at a time.");
        }
        List<VariantRequestDTO> rows = sizes == null ? List.of() : sizes;
        List<List<MultipartFile>> pictures = picturesPerProduct == null ? List.of() : picturesPerProduct;

        // ── 1. Every row, before anything is stored ──────────────────────────
        ProductCreationServiceImpl.checkSizes(rows);
        for (int i = 0; i < products.size(); i++) {
            String row = "Row " + (i + 1) + ": ";
            List<MultipartFile> photos = picturesOf(pictures, i);
            if (photos.size() > ProductCreationServiceImpl.MAX_PICTURES) {
                throw new InvalidRequestException(row + "up to " + ProductCreationServiceImpl.MAX_PICTURES + " pictures.");
            }
            try {
                products.get(i).requestedSalePrice();
                for (int p = 0; p < photos.size(); p++) {
                    ProductCreationServiceImpl.checkPicture(photos.get(p), p + 1);
                }
            } catch (InvalidRequestException e) {
                throw new InvalidRequestException(row + e.getMessage());
            }
        }

        // ── 2. Save them, in order ───────────────────────────────────────────
        List<ProductResponseDTO> created = new ArrayList<>(products.size());
        for (int i = 0; i < products.size(); i++) {
            try {
                created.add(creation.createWithEverything(products.get(i), rows, picturesOf(pictures, i)));
            } catch (InvalidRequestException e) {
                throw new InvalidRequestException("Row " + (i + 1) + ": " + e.getMessage());
            }
        }
        return created;
    }

    private static List<MultipartFile> picturesOf(List<List<MultipartFile>> all, int index) {
        if (index >= all.size() || all.get(index) == null) return List.of();
        return all.get(index).stream().filter(f -> f != null && !f.isEmpty()).toList();
    }
}
