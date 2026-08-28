package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.AddressesDTO;
import kn.org.deliverybackend.entity.Addresses;
import kn.org.deliverybackend.entity.Users;
import kn.org.deliverybackend.exception.AddressLimitExceededException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.mapper.AddressesMapper;
import kn.org.deliverybackend.repository.AddressesRepository;
import kn.org.deliverybackend.repository.UsersRepository;
import kn.org.deliverybackend.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressesRepository addressesRepository;
    private final UsersRepository usersRepository;
    private final AddressesMapper addressesMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AddressesDTO> getAddresses(UUID userId) {
        Optional<Users> optionalUser = usersRepository.findById(userId);
        if (optionalUser.isEmpty()) {
            throw new ResourceNotFoundException("User not found");
        }

        return addressesRepository.findByConsumerId(userId)
                .stream()
                .map(addressesMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AddressesDTO addAddress(UUID userId, AddressesDTO addressesDTO) {
        usersRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (addressesRepository.countByConsumerId(userId) >= 3) {
            throw new AddressLimitExceededException("Maximum 3 addresses allowed");
        }

        Addresses address = addressesMapper.toEntity(addressesDTO);
        address.setConsumerId(userId);

        return addressesMapper.toDTO(addressesRepository.save(address));
    }

    @Override
    @Transactional
    public AddressesDTO updateAddress(UUID userId, UUID addressId, AddressesDTO addressesDTO) {

        Optional<Users> user = usersRepository.findById(userId);
        if (user.isEmpty()) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        Optional<Addresses> address = addressesRepository.findById(addressId);
        if (address.isEmpty()) {
            throw new ResourceNotFoundException("Address not found with id: " + addressId);
        }

        Addresses addressDetails = address.get();

        updateAddressFields(addressDetails, addressesDTO);

        return addressesMapper.toDTO(addressesRepository.save(addressDetails));
    }

    @Override
    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {

        Optional<Users> user = usersRepository.findById(userId);
        if (user.isEmpty()) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        Optional<Addresses> address = addressesRepository.findById(addressId);
        if (address.isEmpty()) {
            throw new ResourceNotFoundException("Address not found with id: " + addressId);
        }
        Addresses addressDetails = address.get();
        addressDetails.setDeleted(true);
        addressDetails.setDeletedAt(new Date());

        addressesRepository.save(addressDetails);
    }

    private void updateAddressFields(Addresses existingAddress, AddressesDTO addressesDTO) {
        if (addressesDTO.getName() != null) {
            existingAddress.setName(addressesDTO.getName());
        }
        if (addressesDTO.getAddress() != null) {
            existingAddress.setAddress(addressesDTO.getAddress());
        }
        if (addressesDTO.getLatitude() != null) {
            existingAddress.setLatitude(addressesDTO.getLatitude());
        }
        if (addressesDTO.getLongitude() != null) {
            existingAddress.setLongitude(addressesDTO.getLongitude());
        }
        if (addressesDTO.getHouseNumber() != null) {
            existingAddress.setHouseNumber(addressesDTO.getHouseNumber());
        }
        if (addressesDTO.getApartmentName() != null) {
            existingAddress.setApartmentName(addressesDTO.getApartmentName());
        }
        if (addressesDTO.getAddressType() != null) {
            existingAddress.setAddressType(addressesDTO.getAddressType());
        }
    }
}
