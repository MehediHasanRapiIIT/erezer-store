package kn.org.deliverybackend.service;

import java.util.Collection;
import java.util.Map;

/**
 * Notifications on the phones running the Erezer app (Firebase Cloud Messaging).
 * Best effort: never throws, and only logs while Firebase is not set up.
 */
public interface PushService {

    /** Sends one notification to each phone; phones Firebase no longer knows are forgotten. */
    void send(Collection<String> tokens, String title, String body, Map<String, String> data);
}
