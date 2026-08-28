package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.coupon.CouponRequestDTO;
import kn.org.deliverybackend.dto.coupon.CouponResponseDTO;
import kn.org.deliverybackend.dto.coupon.CouponValidateRequestDTO;
import kn.org.deliverybackend.dto.coupon.CouponValidateResponseDTO;
import kn.org.deliverybackend.entity.Coupon;
import kn.org.deliverybackend.entity.CouponRedemption;
import kn.org.deliverybackend.enumeration.CouponDiscountType;
import kn.org.deliverybackend.exception.DuplicateResourceException;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.CouponRedemptionRepository;
import kn.org.deliverybackend.repository.CouponRepository;
import kn.org.deliverybackend.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;

    // ── Admin CRUD ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<CouponResponseDTO> list() {
        return couponRepository.findAll().stream()
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .map(this::toDTO).toList();
    }

    @Override
    @Transactional
    public CouponResponseDTO create(CouponRequestDTO request) {
        String code = request.getCode().trim();
        couponRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            throw new DuplicateResourceException("Coupon code '" + code + "' already exists.");
        });
        CouponDiscountType type = CouponDiscountType.parse(request.getDiscountType())
                .orElseThrow(() -> new InvalidStockOperationException(
                        "Unknown discount type: " + request.getDiscountType()));
        validateConfig(type, request.getDiscountValue());

        Coupon c = Coupon.builder()
                .code(code)
                .discountType(type.name())
                .discountValue(request.getDiscountValue())
                .minOrderAmount(request.getMinOrderAmount())
                .usageLimit(request.getUsageLimit())
                .perUserLimit(request.getPerUserLimit())
                .timesUsed(0)
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .description(request.getDescription())
                .build();
        return toDTO(couponRepository.save(c));
    }

    @Override
    @Transactional
    public CouponResponseDTO update(UUID id, CouponRequestDTO request) {
        Coupon c = couponRepository.findById(id)
                .filter(x -> !Boolean.TRUE.equals(x.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + id));

        if (!c.getCode().equalsIgnoreCase(request.getCode())) {
            couponRepository.findByCodeIgnoreCase(request.getCode()).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new DuplicateResourceException(
                            "Coupon code '" + request.getCode() + "' already exists.");
                }
            });
            c.setCode(request.getCode().trim());
        }

        CouponDiscountType type = CouponDiscountType.parse(request.getDiscountType())
                .orElseThrow(() -> new InvalidStockOperationException(
                        "Unknown discount type: " + request.getDiscountType()));
        validateConfig(type, request.getDiscountValue());

        c.setDiscountType(type.name());
        c.setDiscountValue(request.getDiscountValue());
        c.setMinOrderAmount(request.getMinOrderAmount());
        c.setUsageLimit(request.getUsageLimit());
        c.setPerUserLimit(request.getPerUserLimit());
        c.setValidFrom(request.getValidFrom());
        c.setValidTo(request.getValidTo());
        if (request.getIsActive() != null) {
            c.setIsActive(request.getIsActive());
        }
        c.setDescription(request.getDescription());
        return toDTO(couponRepository.save(c));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Coupon c = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + id));
        c.setDeleted(true);
        couponRepository.save(c);
    }

    // ── Public validation ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public CouponValidateResponseDTO validate(CouponValidateRequestDTO request) {
        BigDecimal subtotal = request.getCartSubtotal() != null
                ? request.getCartSubtotal() : BigDecimal.ZERO;

        Coupon c = couponRepository.findByCodeIgnoreCase(request.getCode()).orElse(null);
        if (c == null) {
            return reject("Coupon code is invalid.");
        }
        String reason = checkPolicy(c, subtotal, request.getUserId());
        if (reason != null) {
            return reject(reason);
        }
        CouponDiscountType type = CouponDiscountType.parse(c.getDiscountType())
                .orElseThrow(() -> new IllegalStateException("Bad stored discount type"));

        BigDecimal discount = computeDiscount(type, c.getDiscountValue(), subtotal);
        return CouponValidateResponseDTO.builder()
                .valid(true)
                .code(c.getCode())
                .discountType(type.name())
                .discountAmount(discount)
                .removesShipping(type == CouponDiscountType.FREE_SHIPPING)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Coupon getActiveByCode(String code) {
        Coupon c = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon code is invalid."));
        String reason = checkPolicy(c, BigDecimal.ZERO, null);
        // Re-check active/window only (not min-amount or per-user — those are
        // validated again at the order-quote stage where subtotal is known).
        if (reason != null && !reason.toLowerCase().contains("minimum")
                && !reason.toLowerCase().contains("per-user")) {
            throw new InvalidStockOperationException(reason);
        }
        return c;
    }

    @Override
    @Transactional
    public void recordRedemption(Coupon coupon, UUID userId, UUID orderId, BigDecimal discountAmount) {
        coupon.setTimesUsed((coupon.getTimesUsed() == null ? 0 : coupon.getTimesUsed()) + 1);
        couponRepository.save(coupon);

        redemptionRepository.save(CouponRedemption.builder()
                .couponId(coupon.getId())
                .userId(userId)
                .orderId(orderId)
                .discountAmount(discountAmount == null ? BigDecimal.ZERO : discountAmount)
                .redeemedAt(LocalDateTime.now())
                .build());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** Returns null if the coupon is currently usable; a human reason otherwise. */
    private String checkPolicy(Coupon c, BigDecimal subtotal, UUID userId) {
        if (Boolean.FALSE.equals(c.getIsActive())) {
            return "Coupon is no longer active.";
        }
        LocalDateTime now = LocalDateTime.now();
        if (c.getValidFrom() != null && now.isBefore(c.getValidFrom())) {
            return "Coupon is not yet valid.";
        }
        if (c.getValidTo() != null && now.isAfter(c.getValidTo())) {
            return "Coupon has expired.";
        }
        if (c.getUsageLimit() != null && c.getTimesUsed() != null
                && c.getTimesUsed() >= c.getUsageLimit()) {
            return "Coupon usage limit reached.";
        }
        if (c.getMinOrderAmount() != null && subtotal.compareTo(c.getMinOrderAmount()) < 0) {
            return "Minimum order of " + c.getMinOrderAmount() + " required.";
        }
        if (userId != null && c.getPerUserLimit() != null && c.getPerUserLimit() > 0) {
            long mine = redemptionRepository.countByCouponIdAndUserId(c.getId(), userId);
            if (mine >= c.getPerUserLimit()) {
                return "You've already used this coupon the maximum number of times (per-user limit).";
            }
        }
        return null;
    }

    private BigDecimal computeDiscount(CouponDiscountType type, BigDecimal value, BigDecimal subtotal) {
        return switch (type) {
            case PERCENT -> {
                BigDecimal pct = value == null ? BigDecimal.ZERO : value;
                BigDecimal raw = subtotal.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                // Don't exceed subtotal.
                yield raw.min(subtotal);
            }
            case FLAT -> {
                BigDecimal flat = value == null ? BigDecimal.ZERO : value;
                yield flat.min(subtotal);
            }
            case FREE_SHIPPING -> BigDecimal.ZERO;
        };
    }

    private void validateConfig(CouponDiscountType type, BigDecimal value) {
        switch (type) {
            case PERCENT -> {
                if (value == null || value.compareTo(BigDecimal.ZERO) <= 0
                        || value.compareTo(HUNDRED) > 0) {
                    throw new InvalidStockOperationException(
                            "PERCENT discount value must be in (0, 100].");
                }
            }
            case FLAT -> {
                if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new InvalidStockOperationException(
                            "FLAT discount value must be > 0.");
                }
            }
            case FREE_SHIPPING -> { /* value irrelevant */ }
        }
    }

    private CouponValidateResponseDTO reject(String reason) {
        return CouponValidateResponseDTO.builder()
                .valid(false)
                .discountAmount(BigDecimal.ZERO)
                .removesShipping(false)
                .reason(reason)
                .build();
    }

    private CouponResponseDTO toDTO(Coupon c) {
        return CouponResponseDTO.builder()
                .id(c.getId())
                .code(c.getCode())
                .discountType(c.getDiscountType())
                .discountValue(c.getDiscountValue())
                .minOrderAmount(c.getMinOrderAmount())
                .usageLimit(c.getUsageLimit())
                .perUserLimit(c.getPerUserLimit())
                .timesUsed(c.getTimesUsed())
                .validFrom(c.getValidFrom())
                .validTo(c.getValidTo())
                .isActive(c.getIsActive())
                .description(c.getDescription())
                .build();
    }
}
