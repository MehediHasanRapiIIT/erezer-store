package kn.org.deliverybackend.enumeration;

import java.util.Optional;

public enum ReturnReason {
    WRONG_SIZE,
    DEFECTIVE,
    NOT_AS_DESCRIBED,
    CHANGED_MIND,
    OTHER;

    public static Optional<ReturnReason> parse(String raw) {
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(ReturnReason.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
