package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.discount.DiscountRequestDTO;
import kn.org.deliverybackend.dto.discount.DiscountResponseDTO;
import kn.org.deliverybackend.entity.Discount;

import java.util.List;
import java.util.UUID;

public interface DiscountService {

    // ── Admin CRUD ──────────────────────────────────────────────────────────────
    List<DiscountResponseDTO> list();

    DiscountResponseDTO get(UUID id);

    DiscountResponseDTO create(DiscountRequestDTO request);

    DiscountResponseDTO update(UUID id, DiscountRequestDTO request);

    void delete(UUID id);

    // ── Pricing ───────────────────────────────────────────────────────────────
    /** All discounts that are active, not soft-deleted, and within their validity window now. */
    List<Discount> activeDiscounts();

    /** Currently-active discounts as DTOs, for the public storefront. */
    List<DiscountResponseDTO> activePublic();
}
