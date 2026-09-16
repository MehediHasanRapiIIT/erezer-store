package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.UsersDTO;
import kn.org.deliverybackend.entity.Users;
import kn.org.deliverybackend.exception.DuplicateResourceException;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.mapper.AddressesMapper;
import kn.org.deliverybackend.mapper.UsersMapper;
import kn.org.deliverybackend.repository.AddressesRepository;
import kn.org.deliverybackend.repository.UsersRepository;
import kn.org.deliverybackend.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UsersRepository usersRepository;
    private final AddressesRepository addressesRepository;
    private final UsersMapper usersMapper;
    private final AddressesMapper addressesMapper;
    private final kn.org.deliverybackend.service.CustomerAuthService customerAuthService;

    private static final java.util.regex.Pattern PHONE = java.util.regex.Pattern.compile("^[0-9+\\-\\s()]{7,20}$");
    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

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
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Only these fields can change here, and only on this account: an id,
        // active flag or anything else in the body is ignored.
        if (usersDTO.getFirstName() != null) {
            user.setFirstName(requiredName(usersDTO.getFirstName(), "First name"));
        }
        if (usersDTO.getLastName() != null) {
            user.setLastName(requiredName(usersDTO.getLastName(), "Last name"));
        }
        if (usersDTO.getPhoneNumber() != null) {
            String phone = usersDTO.getPhoneNumber().trim();
            if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) {
                throw new InvalidRequestException("Enter a valid phone number.");
            }
            user.setPhoneNumber(phone.isEmpty() ? null : phone);
        }

        boolean emailChanged = false;
        if (usersDTO.getEmail() != null) {
            String email = usersDTO.getEmail().trim().toLowerCase();
            if (email.isEmpty() || email.length() > 255 || !EMAIL.matcher(email).matches()) {
                throw new InvalidRequestException("Enter a valid email address.");
            }
            if (!email.equals(user.getEmail())) {
                if (usersRepository.existsByEmailAndIdNot(email, userId)) {
                    throw new DuplicateResourceException("An account with this email already exists.");
                }
                // A new address has to be confirmed again before it can be trusted.
                user.setEmail(email);
                user.setEmailVerified(false);
                emailChanged = true;
            }
        }

        Users saved = usersRepository.save(user);
        if (emailChanged) {
            customerAuthService.resendVerificationEmail(saved.getEmail());
        }
        return getProfile(saved.getId());
    }

    private static String requiredName(String value, String label) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidRequestException(label + " is required.");
        }
        if (trimmed.length() > 100) {
            throw new InvalidRequestException(label + " must be 100 characters or fewer.");
        }
        return trimmed;
    }
}
