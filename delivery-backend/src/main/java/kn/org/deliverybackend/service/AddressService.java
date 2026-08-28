package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.AddressesDTO;

import java.util.List;
import java.util.UUID;

public interface AddressService {
    List<AddressesDTO> getAddresses(UUID userId);
    AddressesDTO addAddress(UUID userId, AddressesDTO addressesDTO);
    AddressesDTO updateAddress(UUID userId, UUID addressId, AddressesDTO addressesDTO);
    void deleteAddress(UUID userId, UUID addressId);
}
