package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.discount.DiscountSwitchesDTO;
import kn.org.deliverybackend.dto.settings.StoreSettingsDTO;

public interface StoreSettingsService {

    /** Reads the singleton settings row, seeding sensible defaults on first call. */
    StoreSettingsDTO get();

    /** Admin update of the singleton settings row. */
    StoreSettingsDTO update(StoreSettingsDTO request);

    /** The discount on/off switches. */
    DiscountSwitchesDTO getDiscountSwitches();

    /** Changes the discount switches; a null field is left as it is. */
    DiscountSwitchesDTO updateDiscountSwitches(DiscountSwitchesDTO change);
}
