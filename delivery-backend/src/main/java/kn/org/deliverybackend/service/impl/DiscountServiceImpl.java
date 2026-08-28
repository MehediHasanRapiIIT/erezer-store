package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.discount.DiscountRequestDTO;
import kn.org.deliverybackend.dto.discount.DiscountResponseDTO;
import kn.org.deliverybackend.entity.Discount;
import kn.org.deliverybackend.enumeration.DiscountScope;
import kn.org.deliverybackend.enumeration.DiscountType;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.DiscountRepository;
import kn.org.deliverybackend.service.DiscountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiscountServiceImpl implements DiscountService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final DiscountRepository discountRepository;

    // ── Admin CRUD ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<DiscountResponseDTO> list() {
        return discountRepository.findAll().stream()
                .filter(d -> !Boolean.TRUE.equals(d.getDeleted()))
                .map(this::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DiscountResponseDTO get(UUID id) {
        return toDTO(findActiveById(id));
    }

    @Override
    @Transactional
    public DiscountResponseDTO create(DiscountRequestDTO request) {
        DiscountScope scope = parseScope(request.getScope());
        DiscountType type = parseType(request.getDiscountType());
        validate(scope, type, request);

        Discount d = Discount.builder()
                .name(request.getName().trim())
                .scope(scope.name())
                .discountType(type.name())
                .discountValue(request.getDiscountValue())
                .targetId(scope == DiscountScope.GLOBAL ? null : request.getTargetId())
                .stackable(request.getStackable() != null && request.getStackable())
                .priority(request.getPriority() == null ? 0 : request.getPriority())
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .description(request.getDescription())
                .build();
        return toDTO(discountRepository.save(d));
    }

    @Override
    @Transactional
    public DiscountResponseDTO update(UUID id, DiscountRequestDTO request) {
        Discount d = findActiveById(id);
        DiscountScope scope = parseScope(request.getScope());
        DiscountType type = parseType(request.getDiscountType());
        validate(scope, type, request);

        d.setName(request.getName().trim());
        d.setScope(scope.name());
        d.setDiscountType(type.name());
        d.setDiscountValue(request.getDiscountValue());
        d.setTargetId(scope == DiscountScope.GLOBAL ? null : request.getTargetId());
        d.setStackable(request.getStackable() != null && request.getStackable());
        d.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        d.setValidFrom(request.getValidFrom());
        d.setValidTo(request.getValidTo());
        if (request.getIsActive() != null) {
            d.setIsActive(request.getIsActive());
        }
        d.setDescription(request.getDescription());
        return toDTO(discountRepository.save(d));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Discount d = discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found: " + id));
        d.setDeleted(true);
        discountRepository.save(d);
    }

    // ── Pricing ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<Discount> activeDiscounts() {
        LocalDateTime now = LocalDateTime.now();
        return discountRepository.findActive().stream()
                .filter(d -> d.getValidFrom() == null || !now.isBefore(d.getValidFrom()))
                .filter(d -> d.getValidTo() == null || !now.isAfter(d.getValidTo()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiscountResponseDTO> activePublic() {
        return activeDiscounts().stream().map(this::toDTO).toList();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Discount findActiveById(UUID id) {
        return discountRepository.findById(id)
                .filter(x -> !Boolean.TRUE.equals(x.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found: " + id));
    }

    private DiscountScope parseScope(String raw) {
        return DiscountScope.parse(raw)
                .orElseThrow(() -> new InvalidStockOperationException("Unknown discount scope: " + raw));
    }

    private DiscountType parseType(String raw) {
        return DiscountType.parse(raw)
                .orElseThrow(() -> new InvalidStockOperationException("Unknown discount type: " + raw));
    }

    private void validate(DiscountScope scope, DiscountType type, DiscountRequestDTO request) {
        if (scope != DiscountScope.GLOBAL && request.getTargetId() == null) {
            throw new InvalidStockOperationException(
                    scope.name() + " discount requires a target id (product or category).");
        }
        BigDecimal value = request.getDiscountValue();
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
                    throw new InvalidStockOperationException("FLAT discount value must be > 0.");
                }
            }
        }
    }

    private DiscountResponseDTO toDTO(Discount d) {
        return DiscountResponseDTO.builder()
                .id(d.getId())
                .name(d.getName())
                .scope(d.getScope())
                .discountType(d.getDiscountType())
                .discountValue(d.getDiscountValue())
                .targetId(d.getTargetId())
                .stackable(d.getStackable())
                .priority(d.getPriority())
                .validFrom(d.getValidFrom())
                .validTo(d.getValidTo())
                .isActive(d.getIsActive())
                .description(d.getDescription())
                .build();
    }
}
