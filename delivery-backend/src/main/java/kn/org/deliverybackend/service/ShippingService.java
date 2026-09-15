package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.shipping.ShippingQuote;
import kn.org.deliverybackend.dto.shipping.ShippingRulesChangeDTO;
import kn.org.deliverybackend.dto.shipping.ShippingSettingsDTO;
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

    /**
     * The shipping charge for a zone under the shop's rules: the zone's price,
     * unless "free shipping for all orders" is on, or the free-shipping offer is
     * on and {@code goodsTotal} reaches its minimum.
     *
     * @param goodsTotal what the customer pays for the goods, after every discount
     */
    ShippingQuote quoteShipping(ShippingZone zone, BigDecimal goodsTotal);

    /** The admin Shipping page: every zone and the free-shipping rules. */
    ShippingSettingsDTO settings();

    /** Sets one zone's shipping price. */
    ShippingSettingsDTO updateZoneFee(Long zoneId, BigDecimal flatFee);

    /** Changes the free-shipping rules; null fields stay as they are. */
    ShippingSettingsDTO updateRules(ShippingRulesChangeDTO change);

    /**
     * Compute the tax amount for a given zone + (subtotal - discount) base.
     * Returns zero if no applicable rule.
     */
    BigDecimal computeTax(Long zoneId, BigDecimal taxableAmount);
}
