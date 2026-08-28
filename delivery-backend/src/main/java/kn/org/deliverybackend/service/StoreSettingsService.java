package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.settings.StoreSettingsDTO;

public interface StoreSettingsService {

    /** Reads the singleton settings row, seeding sensible defaults on first call. */
    StoreSettingsDTO get();

    /** Admin update of the singleton settings row. */
    StoreSettingsDTO update(StoreSettingsDTO request);
}
