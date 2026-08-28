package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.bundle.BundleOfferRequestDTO;
import kn.org.deliverybackend.dto.bundle.BundleOfferResponseDTO;
import kn.org.deliverybackend.dto.request.order.OrderItemRequestDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface BundleService {

    // ── Public ────────────────────────────────────────────────────────────────
    List<BundleOfferResponseDTO> listActive();

    BundleOfferResponseDTO getActive(UUID id);

    /** Featured bundle for the landing widget (or null when none). */
    BundleOfferResponseDTO getFeatured();

    // ── Admin ─────────────────────────────────────────────────────────────────
    List<BundleOfferResponseDTO> listAll();

    BundleOfferResponseDTO get(UUID id);

    BundleOfferResponseDTO create(BundleOfferRequestDTO request);

    BundleOfferResponseDTO update(UUID id, BundleOfferRequestDTO request);

    void delete(UUID id);

    // ── Pricing (server-authoritative) ──────────────────────────────────────────
    /**
     * Validates that {@code items} form a legal fill of the bundle (all from the
     * curated products, total quantity == slots) and returns the discount that
     * brings the items' {@code subtotal} down to the fixed bundle price. Throws
     * when the bundle is missing/inactive or the selection is invalid.
     */
    BigDecimal bundleDiscount(UUID bundleId, List<OrderItemRequestDTO> items, BigDecimal subtotal);
}
