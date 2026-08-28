package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.shipping.ShippingZoneDTO;
import kn.org.deliverybackend.entity.ShippingZone;

import java.math.BigDecimal;
import java.util.List;

public interface ShippingService {

    List<ShippingZoneDTO> listActive();

    /**
     * Pick a zone for the given delivery address (case-insensitive keyword match
     * against {@code regionKeywords}). Falls back to {@link ShippingService#defaultZone}.
     */
    ShippingZone resolveZone(String deliveryAddress);

    ShippingZone defaultZone();

    /** Compute the shipping fee for a zone, accounting for the free-above threshold. */
    BigDecimal computeFee(ShippingZone zone, BigDecimal subtotal);

    /**
     * Compute the tax amount for a given zone + (subtotal - discount) base.
     * Returns zero if no applicable rule.
     */
    BigDecimal computeTax(Long zoneId, BigDecimal taxableAmount);
}
