package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.coupon.CouponRequestDTO;
import kn.org.deliverybackend.dto.coupon.CouponResponseDTO;
import kn.org.deliverybackend.dto.coupon.CouponValidateRequestDTO;
import kn.org.deliverybackend.dto.coupon.CouponValidateResponseDTO;
import kn.org.deliverybackend.entity.Coupon;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.UUID;

public interface CouponService {

    // ── Admin CRUD ─────────────────────────────────────────────────────────────
    /** One page of coupons, newest first; {@code q} searches the code and description. */
    Page<CouponResponseDTO> list(String q, int page, int size);
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
