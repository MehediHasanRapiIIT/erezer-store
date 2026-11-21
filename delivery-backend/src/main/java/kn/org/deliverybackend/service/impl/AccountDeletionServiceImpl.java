package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.Users;
import kn.org.deliverybackend.enumeration.CustomOrderStatus;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.CustomerDataRepository;
import kn.org.deliverybackend.repository.UsersRepository;
import kn.org.deliverybackend.service.AccountDeletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Deletes a customer's account the way Google Play and the shop's privacy
 * promise need: the login, profile, saved addresses, cart, design drafts,
 * notifications and newsletter signup go; orders, custom orders and returns
 * stay for the shop's accounts but no longer say who the person was. Reviews
 * stay too (they show "Unknown User" once the account is gone). All of it
 * happens together or not at all.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionServiceImpl implements AccountDeletionService {

    static final String DELETED_NAME = "Deleted customer";
    static final String REMOVED_ADDRESS = "Removed at the customer's request";
    /** Stands in for a custom order's required email; the .invalid domain can never receive mail. */
    static final String REMOVED_EMAIL = "deleted@erezer.invalid";

    private static final List<String> FINISHED_ORDERS = List.of("DELIVERED", "CANCELLED", "RETURNED");
    private static final List<CustomOrderStatus> FINISHED_CUSTOM_ORDERS =
            List.of(CustomOrderStatus.DELIVERED, CustomOrderStatus.CLOSED);

    private final UsersRepository usersRepository;
    private final CustomerDataRepository customerData;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void deleteAccount(UUID userId, String password) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found."));
        if (password == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidRequestException("That password is not right.");
        }

        List<String> open = customerData.openOrderNumbers(userId, FINISHED_ORDERS);
        if (!open.isEmpty()) {
            throw new InvalidRequestException("Your order " + String.join(", ", open)
                    + " is still on its way. You can delete your account once it is delivered or cancelled.");
        }
        if (!customerData.openCustomOrders(userId, FINISHED_CUSTOM_ORDERS).isEmpty()) {
            throw new InvalidRequestException("Your custom design order is still in progress. "
                    + "You can delete your account once it is delivered or closed.");
        }

        int orders = customerData.anonymizeOrders(userId, DELETED_NAME, REMOVED_ADDRESS);
        customerData.anonymizeCustomOrders(userId, REMOVED_EMAIL, REMOVED_ADDRESS);
        customerData.anonymizeReturns(userId);
        customerData.deleteAddresses(userId);
        customerData.deleteCart(userId);
        customerData.deleteDesignDrafts(userId);
        customerData.deleteNotifications(userId);
        customerData.deleteOneTimeCodes(userId);
        customerData.deletePushDevices(userId);
        if (user.getEmail() != null) customerData.deleteNewsletterSignup(user.getEmail());
        customerData.deleteAccount(userId);
        // No email or name in the log: the point is that they are gone.
        log.info("[account] Customer account {} deleted at their request; {} past order(s) kept without personal details",
                userId, orders);
    }
}
