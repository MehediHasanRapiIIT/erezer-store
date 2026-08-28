package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.flashsale.FlashSaleRequestDTO;
import kn.org.deliverybackend.dto.flashsale.FlashSaleResponseDTO;
import kn.org.deliverybackend.entity.FlashSale;
import kn.org.deliverybackend.enumeration.DiscountType;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.FlashSaleRepository;
import kn.org.deliverybackend.service.FlashSaleService;
import kn.org.deliverybackend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FlashSaleServiceImpl implements FlashSaleService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final FlashSaleRepository flashSaleRepository;
    private final ProductService productService;

    // ── Admin CRUD ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<FlashSaleResponseDTO> list() {
        return flashSaleRepository.findAll().stream()
                .filter(f -> !Boolean.TRUE.equals(f.getDeleted()))
                .sorted(Comparator.comparing(FlashSale::getEndsAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FlashSaleResponseDTO get(UUID id) {
        return toDTO(findActiveById(id));
    }

    @Override
    @Transactional
    public FlashSaleResponseDTO create(FlashSaleRequestDTO request) {
        DiscountType type = parseType(request.getDiscountType());
        validate(type, request);

        FlashSale f = FlashSale.builder()
                .name(request.getName().trim())
                .label(trimToNull(request.getLabel()))
                .discountType(type.name())
                .discountValue(request.getDiscountValue())
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .couponCode(normaliseCoupon(request.getCouponCode()))
                .minSpend(request.getMinSpend())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .featured(Boolean.TRUE.equals(request.getFeatured()))
                .productIds(cleanIds(request.getProductIds()))
                .build();
        return toDTO(flashSaleRepository.save(f));
    }

    @Override
    @Transactional
    public FlashSaleResponseDTO update(UUID id, FlashSaleRequestDTO request) {
        FlashSale f = findActiveById(id);
        DiscountType type = parseType(request.getDiscountType());
        validate(type, request);

        f.setName(request.getName().trim());
        f.setLabel(trimToNull(request.getLabel()));
        f.setDiscountType(type.name());
        f.setDiscountValue(request.getDiscountValue());
        f.setStartsAt(request.getStartsAt());
        f.setEndsAt(request.getEndsAt());
        f.setCouponCode(normaliseCoupon(request.getCouponCode()));
        f.setMinSpend(request.getMinSpend());
        if (request.getIsActive() != null) {
            f.setIsActive(request.getIsActive());
        }
        f.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
        // Replace the participating set in place so the @OrderColumn list re-indexes.
        f.getProductIds().clear();
        f.getProductIds().addAll(cleanIds(request.getProductIds()));
        return toDTO(flashSaleRepository.save(f));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        FlashSale f = flashSaleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flash sale not found: " + id));
        f.setDeleted(true);
        flashSaleRepository.save(f);
    }

    // ── Public storefront ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<FlashSaleResponseDTO> listActivePublic() {
        // Includes upcoming (not-yet-started) sales — anything active that hasn't
        // ended. The storefront card labels not-yet-started ones as "Starts …".
        return activeNotEnded()
                // Featured first, then soonest-ending.
                .sorted(Comparator
                        .comparing((FlashSale f) -> !Boolean.TRUE.equals(f.getFeatured()))
                        .thenComparing(FlashSale::getEndsAt))
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FlashSaleResponseDTO getFeaturedPublic() {
        List<FlashSale> active = activeInWindow().toList();
        // Prefer an admin-featured sale; otherwise the one ending soonest.
        return active.stream()
                .filter(f -> Boolean.TRUE.equals(f.getFeatured()))
                .min(Comparator.comparing(FlashSale::getEndsAt))
                .or(() -> active.stream().min(Comparator.comparing(FlashSale::getEndsAt)))
                .map(this::toDTO)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public FlashSaleResponseDTO getPublicById(UUID id) {
        LocalDateTime now = LocalDateTime.now();
        return flashSaleRepository.findById(id)
                .filter(f -> !Boolean.TRUE.equals(f.getDeleted()))
                .filter(f -> Boolean.TRUE.equals(f.getIsActive()))
                // Not viewable before it starts (matches the non-clickable list card);
                // ended sales still resolve so the page can show its "ended" state.
                .filter(f -> f.getStartsAt() == null || !now.isBefore(f.getStartsAt()))
                .map(this::toDTO)
                .orElse(null);
    }

    /** Active, non-deleted sales whose [startsAt, endsAt) window covers now (currently live). */
    private java.util.stream.Stream<FlashSale> activeInWindow() {
        LocalDateTime now = LocalDateTime.now();
        return flashSaleRepository.findActive().stream()
                .filter(f -> f.getStartsAt() == null || !now.isBefore(f.getStartsAt()))
                .filter(f -> f.getEndsAt() != null && now.isBefore(f.getEndsAt()));
    }

    /** Active, non-deleted sales that have not yet ended — live + scheduled-upcoming. */
    private java.util.stream.Stream<FlashSale> activeNotEnded() {
        LocalDateTime now = LocalDateTime.now();
        return flashSaleRepository.findActive().stream()
                .filter(f -> f.getEndsAt() != null && now.isBefore(f.getEndsAt()));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private FlashSale findActiveById(UUID id) {
        return flashSaleRepository.findById(id)
                .filter(x -> !Boolean.TRUE.equals(x.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Flash sale not found: " + id));
    }

    private DiscountType parseType(String raw) {
        return DiscountType.parse(raw)
                .orElseThrow(() -> new InvalidStockOperationException("Unknown discount type: " + raw));
    }

    private void validate(DiscountType type, FlashSaleRequestDTO request) {
        BigDecimal value = request.getDiscountValue();
        switch (type) {
            case PERCENT -> {
                if (value == null || value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(HUNDRED) > 0) {
                    throw new InvalidStockOperationException("PERCENT discount value must be in (0, 100].");
                }
            }
            case FLAT -> {
                if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new InvalidStockOperationException("FLAT discount value must be > 0.");
                }
            }
        }
        if (request.getEndsAt() == null) {
            throw new InvalidStockOperationException("A flash sale requires an end date/time.");
        }
        if (request.getStartsAt() != null && !request.getStartsAt().isBefore(request.getEndsAt())) {
            throw new InvalidStockOperationException("Start must be before end.");
        }
        if (request.getProductIds() == null || cleanIds(request.getProductIds()).isEmpty()) {
            throw new InvalidStockOperationException("Select at least one product for the sale.");
        }
    }

    /** De-dupe while preserving order; drop nulls. */
    private List<Long> cleanIds(List<Long> ids) {
        if (ids == null) return new ArrayList<>();
        List<Long> out = new ArrayList<>();
        for (Long id : ids) {
            if (id != null && !out.contains(id)) out.add(id);
        }
        return out;
    }

    private String normaliseCoupon(String raw) {
        String v = trimToNull(raw);
        return v == null ? null : v.toUpperCase();
    }

    private String trimToNull(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        return v.isEmpty() ? null : v;
    }

    private FlashSaleResponseDTO toDTO(FlashSale f) {
        return FlashSaleResponseDTO.builder()
                .id(f.getId())
                .name(f.getName())
                .label(f.getLabel())
                .discountType(f.getDiscountType())
                .discountValue(f.getDiscountValue())
                .startsAt(f.getStartsAt())
                .endsAt(f.getEndsAt())
                .couponCode(f.getCouponCode())
                .minSpend(f.getMinSpend())
                .isActive(f.getIsActive())
                .featured(f.getFeatured())
                .products(productService.getProductsByIds(f.getProductIds()))
                .build();
    }
}
