package kn.org.deliverybackend.service;

import java.util.UUID;

/** A customer deleting their own account (website and app). */
public interface AccountDeletionService {

    /**
     * Deletes the account after checking its password. Past orders stay for the
     * shop's records with the person's name, email, phone and address taken out.
     * Refused while an order is still on its way.
     */
    void deleteAccount(UUID userId, String password);
}
