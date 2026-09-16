package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.flashsale.FlashSaleRequestDTO;
import kn.org.deliverybackend.dto.flashsale.FlashSaleResponseDTO;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface FlashSaleService {

    // ── Admin CRUD ──────────────────────────────────────────────────────────────
    /** One page of flash sales, latest-ending first; {@code q} searches name, label and coupon. */
    Page<FlashSaleResponseDTO> list(String q, int page, int size);

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
