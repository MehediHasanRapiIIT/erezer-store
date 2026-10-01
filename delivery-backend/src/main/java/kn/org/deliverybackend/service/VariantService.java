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
}
