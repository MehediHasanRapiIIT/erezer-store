package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.coupon.CouponRequestDTO;
import kn.org.deliverybackend.dto.coupon.CouponResponseDTO;
import kn.org.deliverybackend.dto.coupon.CouponValidateRequestDTO;
import kn.org.deliverybackend.dto.coupon.CouponValidateResponseDTO;
import kn.org.deliverybackend.entity.Coupon;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CouponService {

    // ── Admin CRUD ─────────────────────────────────────────────────────────────
    List<CouponResponseDTO> list();
    CouponResponseDTO create(CouponRequestDTO request);
    CouponResponseDTO update(UUID id, CouponRequestDTO request);
    void delete(UUID id);

    // ── Public validation ──────────────────────────────────────────────────────
    CouponValidateResponseDTO validate(CouponValidateRequestDTO request);

    // ── Internal redemption (called from OrderServiceImpl after the order is saved) ──
    void recordRedemption(Coupon coupon, UUID userId, UUID orderId, BigDecimal discountAmount);

    /** Re-fetch a coupon by code, throwing if it is missing or inactive. */
    Coupon getActiveByCode(String code);
}
