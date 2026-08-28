package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.UsersDTO;
import kn.org.deliverybackend.entity.Users;
import kn.org.deliverybackend.exception.DuplicateResourceException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.mapper.AddressesMapper;
import kn.org.deliverybackend.mapper.UsersMapper;
import kn.org.deliverybackend.repository.AddressesRepository;
import kn.org.deliverybackend.repository.UsersRepository;
import kn.org.deliverybackend.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UsersRepository usersRepository;
    private final AddressesRepository addressesRepository;
    private final UsersMapper usersMapper;
    private final AddressesMapper addressesMapper;

    @Override
    @Transactional(readOnly = true)
    public UsersDTO getProfile(UUID userId) {
        return usersRepository.findById(userId)
                .map(users -> {
                    UsersDTO userDTO = usersMapper.toDTO(users);
                    userDTO.setAddresses(addressesRepository.findByConsumerId(userId)
                            .stream()
                            .map(addressesMapper::toDTO)
                            .collect(Collectors.toList()));
                    return userDTO;
                })
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    @Override
    @Transactional
    public UsersDTO updateProfile(UUID userId, UsersDTO usersDTO) {

        Optional<Users> optionalUsers = usersRepository.findById(userId);

        if (optionalUsers.isEmpty()) {

            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        Users existingUser = optionalUsers.get();

        updateUserFields(existingUser, usersDTO);

        return usersMapper
                .toDTO(usersRepository
                        .save(usersMapper
                                .toEntity(usersDTO)));
    }

    private void updateUserFields(Users existingUser, UsersDTO usersDTO) {
        if (usersDTO.getPhoneNumber() != null) {
            existingUser.setPhoneNumber(usersDTO.getPhoneNumber());
        }
        if (usersDTO.getFirstName() != null) {
            existingUser.setFirstName(usersDTO.getFirstName());
        }
        if (usersDTO.getLastName() != null) {
            existingUser.setLastName(usersDTO.getLastName());
        }
        if (usersDTO.getEmail() != null) {
            existingUser.setEmail(usersDTO.getEmail());
        }
        if (usersDTO.getProfileImage() != null) {
            existingUser.setProfileImage(usersDTO.getProfileImage());
        }
    }
}
