package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.request.product.ProductRequestDTO;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Adds a product with its pictures and sizes in one go. */
public interface ProductCreationService {

    /**
     * Saves the product, its pictures (the first is the main one, in the order
     * given) and its sizes — all of it, or none of it. Nothing appears in the
     * shop half-made, and no picture is left in storage for a product that
     * never got saved.
     */
    ProductResponseDTO createWithEverything(ProductRequestDTO product,
                                            List<VariantRequestDTO> sizes,
                                            List<MultipartFile> pictures);
}
