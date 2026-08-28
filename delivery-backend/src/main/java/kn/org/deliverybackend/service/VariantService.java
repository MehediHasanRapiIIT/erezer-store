package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import kn.org.deliverybackend.dto.variant.VariantResponseDTO;

import java.util.List;

public interface VariantService {

    List<VariantResponseDTO> listForProduct(Long productId);

    VariantResponseDTO create(Long productId, VariantRequestDTO request);

    VariantResponseDTO update(Long productId, Long variantId, VariantRequestDTO request);

    void delete(Long productId, Long variantId);
}
