package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.shipping.ShippingZoneDTO;
import kn.org.deliverybackend.entity.ShippingZone;
import kn.org.deliverybackend.entity.TaxRule;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.ShippingZoneRepository;
import kn.org.deliverybackend.repository.TaxRuleRepository;
import kn.org.deliverybackend.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShippingServiceImpl implements ShippingService {

    private final ShippingZoneRepository zoneRepository;
    private final TaxRuleRepository taxRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ShippingZoneDTO> listActive() {
        return zoneRepository.findAllActive().stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ShippingZone resolveZone(String deliveryAddress) {
        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            return defaultZone();
        }
        String addr = deliveryAddress.toLowerCase();
        return zoneRepository.findAllActive().stream()
                .filter(z -> z.getRegionKeywords() != null && !z.getRegionKeywords().isBlank())
                .filter(z -> matchesAnyKeyword(addr, z.getRegionKeywords()))
                .findFirst()
                .orElseGet(this::defaultZone);
    }

    @Override
    @Transactional(readOnly = true)
    public ShippingZone defaultZone() {
        return zoneRepository.findDefault()
                .or(() -> zoneRepository.findAllActive().stream().findFirst())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No shipping zones configured. Create one in the admin panel."));
    }

    @Override
    public BigDecimal computeFee(ShippingZone zone, BigDecimal subtotal) {
        if (zone == null) return BigDecimal.ZERO;
        if (zone.getFreeAbove() != null && subtotal != null
                && subtotal.compareTo(zone.getFreeAbove()) >= 0) {
            return BigDecimal.ZERO;
        }
        return zone.getFlatFee() != null ? zone.getFlatFee() : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal computeTax(Long zoneId, BigDecimal taxableAmount) {
        if (taxableAmount == null || taxableAmount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        TaxRule rule = taxRepository.findApplicableForZone(zoneId).orElse(null);
        if (rule == null || Boolean.TRUE.equals(rule.getIsInclusive())) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = rule.getRate() == null ? BigDecimal.ZERO : rule.getRate();
        return taxableAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private boolean matchesAnyKeyword(String lowercaseAddress, String csv) {
        for (String raw : csv.split(",")) {
            String kw = raw.trim().toLowerCase();
            if (!kw.isEmpty() && lowercaseAddress.contains(kw)) return true;
        }
        return false;
    }

    private ShippingZoneDTO toDTO(ShippingZone z) {
        return ShippingZoneDTO.builder()
                .id(z.getId())
                .code(z.getCode())
                .displayName(z.getDisplayName())
                .countryCode(z.getCountryCode())
                .flatFee(z.getFlatFee())
                .freeAbove(z.getFreeAbove())
                .isDefault(z.getIsDefault())
                .isActive(z.getIsActive())
                .build();
    }
}
