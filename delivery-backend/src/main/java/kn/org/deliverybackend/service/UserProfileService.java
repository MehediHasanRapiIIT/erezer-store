package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.UsersDTO;

import java.util.UUID;

public interface UserProfileService {
    UsersDTO getProfile(UUID userId);
    UsersDTO updateProfile(UUID userId, UsersDTO usersDTO);
}
