package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import kn.org.deliverybackend.dto.variant.VariantResponseDTO;

import java.util.List;

public interface VariantService {

    List<VariantResponseDTO> listForProduct(Long productId);

    VariantResponseDTO create(Long productId, VariantRequestDTO request);

    /**
     * Several sizes at once, all or none. Refuses a size the product already
     * has, or one listed twice, before anything is saved.
     */
    List<VariantResponseDTO> createAll(Long productId, List<VariantRequestDTO> requests);

    VariantResponseDTO update(Long productId, Long variantId, VariantRequestDTO request);

    void delete(Long productId, Long variantId);

    /**
     * Sets the fits a product comes in. Sizes with no fit become the first
     * fit; a fit being added gets every size the product has, with no stock; a
     * fit being removed goes with its stock. No fits at all folds everything
     * back into plain sizes, adding the fits' stock together.
     */
    List<VariantResponseDTO> setFits(Long productId, kn.org.deliverybackend.dto.variant.ProductFitsRequestDTO request);
}
