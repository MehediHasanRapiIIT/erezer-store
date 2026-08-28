package kn.org.deliverybackend.enumeration;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Return-request lifecycle.
 *
 * Allowed transitions:
 *   REQUESTED  → APPROVED, REJECTED
 *   APPROVED   → PICKED_UP, REJECTED   (admin may revoke before pickup)
 *   PICKED_UP  → REFUNDED, REJECTED    (admin may reject if items not as described)
 *   REJECTED, REFUNDED → terminal
 */
public enum ReturnStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    PICKED_UP,
    REFUNDED;

    private static final Map<ReturnStatus, Set<ReturnStatus>> ALLOWED = Map.of(
            REQUESTED, EnumSet.of(APPROVED, REJECTED),
            APPROVED,  EnumSet.of(PICKED_UP, REJECTED),
            PICKED_UP, EnumSet.of(REFUNDED, REJECTED)
            // REJECTED, REFUNDED → terminal
    );

    public Set<ReturnStatus> nextStates() {
        return ALLOWED.getOrDefault(this, Set.of());
    }

    public boolean canTransitionTo(ReturnStatus target) {
        return nextStates().contains(target);
    }

    public boolean isTerminal() {
        return nextStates().isEmpty();
    }

    public static Optional<ReturnStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(ReturnStatus.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
