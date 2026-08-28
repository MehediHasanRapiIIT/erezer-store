package kn.org.deliverybackend.enumeration;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Erezer order lifecycle.
 *
 * Each constant declares the set of statuses it may transition into. Statuses
 * without an entry in {@link #ALLOWED_TRANSITIONS} are terminal.
 *
 * Legacy data from the delivery-app era used "PENDING" as the initial status.
 * That value is retained here as a deprecated alias so existing rows still
 * parse; new orders are created with {@link #PLACED}.
 */
public enum OrderStatus {
    PLACED,
    ACCEPTED,
    IN_PRODUCTION,
    PROCESSING,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    RETURNED,

    /** @deprecated legacy initial state from the delivery-app era. Treated as {@link #PLACED}. */
    @Deprecated
    PENDING;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            PLACED,           EnumSet.of(ACCEPTED, CANCELLED),
            PENDING,          EnumSet.of(ACCEPTED, CANCELLED),
            ACCEPTED,         EnumSet.of(IN_PRODUCTION, CANCELLED),
            IN_PRODUCTION,    EnumSet.of(PROCESSING, CANCELLED),
            PROCESSING,       EnumSet.of(SHIPPED, CANCELLED),
            SHIPPED,          EnumSet.of(OUT_FOR_DELIVERY, DELIVERED, RETURNED),
            OUT_FOR_DELIVERY, EnumSet.of(DELIVERED, RETURNED),
            DELIVERED,        EnumSet.of(RETURNED)
            // CANCELLED and RETURNED are terminal.
    );

    private static final Set<OrderStatus> CANCELLABLE_BY_CUSTOMER =
            EnumSet.of(PLACED, PENDING, ACCEPTED);

    public Set<OrderStatus> nextStates() {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of());
    }

    public boolean canTransitionTo(OrderStatus next) {
        return nextStates().contains(next);
    }

    public boolean isTerminal() {
        return nextStates().isEmpty();
    }

    public boolean isCancellableByCustomer() {
        return CANCELLABLE_BY_CUSTOMER.contains(this);
    }

    /**
     * Parse a status string, accepting any case and the legacy "PENDING" alias.
     * Returns empty for unknown input — callers should reject with a 400.
     */
    public static Optional<OrderStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(OrderStatus.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** Normalize legacy PENDING → PLACED for display & comparison purposes. */
    public OrderStatus normalize() {
        return this == PENDING ? PLACED : this;
    }
}
