package kn.org.deliverybackend.dto.shipping;

import java.math.BigDecimal;

/** A change to the free-shipping rules. A null field stays as it is. */
public record ShippingRulesChangeDTO(Boolean freeAll, Boolean offerEnabled, BigDecimal offerMin) {
}
