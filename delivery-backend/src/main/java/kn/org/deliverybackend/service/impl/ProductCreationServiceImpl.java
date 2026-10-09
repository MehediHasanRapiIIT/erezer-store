package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.product.ProductRequestDTO;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.service.FileStorageService;
import kn.org.deliverybackend.service.ProductCreationService;
import kn.org.deliverybackend.service.ProductImageService;
import kn.org.deliverybackend.service.ProductService;
import kn.org.deliverybackend.service.VariantService;
import kn.org.deliverybackend.util.ImageUploads;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Add product" in one click: the product, its pictures and its sizes.
 *
 * <p>The database part is one transaction, so either everything is saved or
 * nothing is. Pictures live in file storage, which a rollback can't reach, so
 * every stored address is remembered and removed again if the save does not
 * go through.
 *
 * <p>Whatever can be checked without storing anything is checked first — every
 * picture really is a picture, the discount makes sense, no size is listed
 * twice — so a typical mistake costs nothing at all.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCreationServiceImpl implements ProductCreationService {

    /** More than enough for a product page, and keeps one save well under the upload limit. */
    static final int MAX_PICTURES = 10;

    private final ProductService productService;
    private final ProductImageService imageService;
    private final VariantService variantService;
    private final FileStorageService fileStorageService;
    private final ProductOptionsService productOptionsService;

    @Override
    @Transactional
    public ProductResponseDTO createWithEverything(ProductRequestDTO product,
                                                   List<VariantRequestDTO> sizes,
                                                   List<MultipartFile> pictures) {
        List<MultipartFile> photos = pictures == null ? List.of()
                : pictures.stream().filter(f -> f != null && !f.isEmpty()).toList();
        List<VariantRequestDTO> rows = sizes == null ? List.of() : sizes;

        // ── 1. Everything that can be checked before anything is stored ──────
        if (photos.size() > MAX_PICTURES) {
            throw new InvalidRequestException("Up to " + MAX_PICTURES + " pictures per product.");
        }
        for (int i = 0; i < photos.size(); i++) {
            checkPicture(photos.get(i), i + 1);
        }
        product.requestedSalePrice();   // refuses a discount that makes no sense
        checkSizes(rows);

        // ── 2. Tidy up storage if the save is abandoned ──────────────────────
        List<String> stored = Collections.synchronizedList(new ArrayList<>());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) return;
                for (String url : stored) {
                    try {
                        fileStorageService.deleteByUrl(url);
                    } catch (RuntimeException e) {
                        log.warn("Couldn't remove {} after an abandoned product save", url, e);
                    }
                }
            }
        });

        // ── 3. The save itself ───────────────────────────────────────────────
        ProductResponseDTO created = productService.createProduct(product, null);
        Long id = created.getId();
        for (int i = 0; i < photos.size(); i++) {
            String url = fileStorageService.uploadFile(photos.get(i));
            stored.add(url);
            imageService.attach(id, url, null, i, i == 0);
        }
        // Options first, then sizes: each size is then added in every
        // combination, with the stock typed for it.
        if (product.getOptions() != null && !product.getOptions().isEmpty()) {
            productOptionsService.set(id, product.getOptions(), false);
        }
        if (!rows.isEmpty()) {
            variantService.createAll(id, rows);
        }
        return productService.getProductById(id);
    }

    /** Refuses anything that isn't a picture before a single file is stored. */
    static void checkPicture(MultipartFile file, int position) {
        try (InputStream in = file.getInputStream()) {
            ImageUploads.check(file.getSize(), in.readNBytes(16));
        } catch (InvalidRequestException e) {
            throw new InvalidRequestException("Picture " + position + ": " + e.getMessage());
        } catch (IOException e) {
            throw new InvalidRequestException("Picture " + position + " couldn't be read.");
        }
    }

    /** Every size row has a size, and none is listed twice. */
    static void checkSizes(List<VariantRequestDTO> rows) {
        Set<String> seen = new HashSet<>();
        for (VariantRequestDTO r : rows) {
            String size = r.getSize() == null ? "" : r.getSize().trim();
            if (size.isEmpty()) {
                throw new InvalidRequestException("Every size row needs a size.");
            }
            kn.org.deliverybackend.enumeration.Fit fit = kn.org.deliverybackend.enumeration.Fit.parse(r.getFit());
            if (!seen.add((fit == null ? "" : fit.name()) + "|" + size.toUpperCase())) {
                throw new InvalidRequestException((fit == null ? "Size " : fit.label() + " ") + size + " is listed twice.");
            }
        }
    }
}
