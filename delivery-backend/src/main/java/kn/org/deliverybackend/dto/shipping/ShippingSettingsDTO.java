package kn.org.deliverybackend.dto.shipping;

import java.math.BigDecimal;
import java.util.List;

/** Everything on the admin Shipping page: each zone's price, and the free-shipping rules. */
public record ShippingSettingsDTO(List<ShippingZoneDTO> zones,
                                  boolean freeAll,
                                  boolean offerEnabled,
                                  BigDecimal offerMin) {
}
