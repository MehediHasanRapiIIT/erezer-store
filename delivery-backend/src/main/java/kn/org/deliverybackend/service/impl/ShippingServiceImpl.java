package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.shipping.ShippingQuote;
import kn.org.deliverybackend.dto.shipping.ShippingRulesChangeDTO;
import kn.org.deliverybackend.dto.shipping.ShippingSettingsDTO;
import kn.org.deliverybackend.dto.shipping.ShippingZoneDTO;
import kn.org.deliverybackend.entity.ShippingZone;
import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.entity.TaxRule;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.ShippingZoneRepository;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
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
    private final StoreSettingsRepository settingsRepository;

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
    @Transactional(readOnly = true)
    public ShippingQuote quoteShipping(ShippingZone zone, BigDecimal goodsTotal) {
        StoreSettings s = settingsRepository.findById(StoreSettings.SINGLETON_ID).orElse(null);
        boolean freeAll = s != null && Boolean.TRUE.equals(s.getShippingFreeAll());
        BigDecimal offerMin = s != null && Boolean.TRUE.equals(s.getShippingOfferEnabled())
                && s.getShippingOfferMin() != null && s.getShippingOfferMin().signum() > 0
                ? s.getShippingOfferMin() : null;

        if (freeAll) {
            return new ShippingQuote(BigDecimal.ZERO, ShippingQuote.FREE_ALL, offerMin);
        }
        if (offerMin != null && goodsTotal != null && goodsTotal.compareTo(offerMin) >= 0) {
            return new ShippingQuote(BigDecimal.ZERO, ShippingQuote.OFFER, offerMin);
        }
        // Zones used to carry their own free-above threshold; it is no longer read
        // (V16), so the only ways to free shipping are the two rules above.
        BigDecimal fee = zone != null && zone.getFlatFee() != null ? zone.getFlatFee() : BigDecimal.ZERO;
        return new ShippingQuote(fee, null, offerMin);
    }

    @Override
    @Transactional(readOnly = true)
    public ShippingSettingsDTO settings() {
        StoreSettings s = settingsRepository.findById(StoreSettings.SINGLETON_ID).orElse(null);
        return new ShippingSettingsDTO(
                zoneRepository.findAllForAdmin().stream().map(this::toDTO).toList(),
                s != null && Boolean.TRUE.equals(s.getShippingFreeAll()),
                s != null && Boolean.TRUE.equals(s.getShippingOfferEnabled()),
                s == null ? null : s.getShippingOfferMin());
    }

    @Override
    @Transactional
    public ShippingSettingsDTO updateZoneFee(Long zoneId, BigDecimal flatFee) {
        ShippingZone zone = zoneRepository.findById(zoneId)
                .filter(z -> !Boolean.TRUE.equals(z.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Shipping zone not found: " + zoneId));
        if (flatFee == null || flatFee.signum() < 0) {
            throw new InvalidRequestException("The shipping price can't be negative.");
        }
        zone.setFlatFee(flatFee.setScale(2, RoundingMode.HALF_UP));
        zoneRepository.save(zone);
        return settings();
    }

    @Override
    @Transactional
    public ShippingSettingsDTO updateRules(ShippingRulesChangeDTO change) {
        StoreSettings s = settingsRepository.findById(StoreSettings.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Store settings are not set up yet."));
        if (change.offerMin() != null) {
            if (change.offerMin().signum() <= 0) {
                throw new InvalidRequestException("The free-shipping amount must be more than ৳0.");
            }
            s.setShippingOfferMin(change.offerMin().setScale(2, RoundingMode.HALF_UP));
        }
        if (change.offerEnabled() != null) {
            if (change.offerEnabled() && (s.getShippingOfferMin() == null || s.getShippingOfferMin().signum() <= 0)) {
                throw new InvalidRequestException("Set the order amount for free shipping before turning the offer on.");
            }
            s.setShippingOfferEnabled(change.offerEnabled());
        }
        if (change.freeAll() != null) {
            s.setShippingFreeAll(change.freeAll());
        }
        settingsRepository.save(s);
        return settings();
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
                .isDefault(z.getIsDefault())
                .isActive(z.getIsActive())
                .build();
    }
}
