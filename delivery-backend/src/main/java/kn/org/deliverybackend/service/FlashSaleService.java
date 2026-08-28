package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.flashsale.FlashSaleRequestDTO;
import kn.org.deliverybackend.dto.flashsale.FlashSaleResponseDTO;

import java.util.List;
import java.util.UUID;

public interface FlashSaleService {

    // ── Admin CRUD ──────────────────────────────────────────────────────────────
    List<FlashSaleResponseDTO> list();

    FlashSaleResponseDTO get(UUID id);

    FlashSaleResponseDTO create(FlashSaleRequestDTO request);

    FlashSaleResponseDTO update(UUID id, FlashSaleRequestDTO request);

    void delete(UUID id);

    // ── Public storefront ───────────────────────────────────────────────────────
    /** All active flash sales whose window covers now (featured first, then soonest-ending). */
    List<FlashSaleResponseDTO> listActivePublic();

    /** The featured active sale for the landing widget (falls back to soonest-ending), or {@code null}. */
    FlashSaleResponseDTO getFeaturedPublic();

    /** A single active, non-deleted flash sale by id for the public detail page, or {@code null}. */
    FlashSaleResponseDTO getPublicById(UUID id);
}
