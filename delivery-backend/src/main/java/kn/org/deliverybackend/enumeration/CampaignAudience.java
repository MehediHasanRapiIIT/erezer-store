package kn.org.deliverybackend.enumeration;

import java.util.Optional;

public enum CampaignAudience {
    /** Every active newsletter_subscriber row. */
    ALL_SUBSCRIBERS,
    /** Every registered customer (users.email) that hasn't unsubscribed. */
    REGISTERED_CUSTOMERS;

    public static Optional<CampaignAudience> parse(String raw) {
        if (raw == null) return Optional.empty();
        try { return Optional.of(CampaignAudience.valueOf(raw.trim().toUpperCase())); }
        catch (IllegalArgumentException ex) { return Optional.empty(); }
    }
}
