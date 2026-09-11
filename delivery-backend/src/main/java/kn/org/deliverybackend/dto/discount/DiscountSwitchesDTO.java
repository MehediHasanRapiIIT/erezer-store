package kn.org.deliverybackend.dto.discount;

import kn.org.deliverybackend.dto.settings.StoreSettingsDTO;

/**
 * The discount on/off switches, kept apart from the rest of store settings so
 * they can be given to staff on their own. On save, a null field means
 * "leave as it is", so one switch can be flipped without sending the others.
 */
public record DiscountSwitchesDTO(Boolean discountsEnabled,
                                  Boolean discountsGlobalEnabled,
                                  Boolean discountsCategoryEnabled,
                                  Boolean discountsProductEnabled) {

    public static DiscountSwitchesDTO from(StoreSettingsDTO settings) {
        return new DiscountSwitchesDTO(settings.getDiscountsEnabled(), settings.getDiscountsGlobalEnabled(),
                settings.getDiscountsCategoryEnabled(), settings.getDiscountsProductEnabled());
    }
}
