package kn.org.deliverybackend.payment.bkash;

import kn.org.deliverybackend.entity.Payment;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * For a bKash order, whether bKash has taken the money: PAID once a payment completed,
 * FAILED when the last try failed or was cancelled, PENDING when none finished (never
 * started, or left on bKash's page). Null for other payment methods. Shown to the
 * customer ("Payment failed — pay again") and to staff ("Unpaid").
 */
public final class BkashPaymentState {

    private BkashPaymentState() {
    }

    /** The bKash payment that completed, if one did. */
    public static java.util.Optional<Payment> completed(List<Payment> payments) {
        return payments == null ? java.util.Optional.empty()
                : payments.stream().filter(p -> "BKASH".equalsIgnoreCase(p.getProvider())
                        && "Completed".equalsIgnoreCase(p.getStatus())).findFirst();
    }

    /** A wallet number with the middle hidden, for anything the customer is sent: 01770618575 → 017*****575. */
    public static String masked(String wallet) {
        if (wallet == null || wallet.length() < 7) return wallet;
        return wallet.substring(0, 3) + "*".repeat(wallet.length() - 6) + wallet.substring(wallet.length() - 3);
    }

    public static String of(String paymentMethod, List<Payment> payments) {
        if (!"BKASH".equalsIgnoreCase(paymentMethod)) return null;
        List<Payment> bkash = payments == null ? List.of()
                : payments.stream().filter(p -> "BKASH".equalsIgnoreCase(p.getProvider())).toList();
        if (bkash.stream().anyMatch(p -> "Completed".equalsIgnoreCase(p.getStatus()))) return "PAID";
        String last = bkash.stream()
                .filter(p -> p.getCreatedAt() != null)
                .max(Comparator.comparing(Payment::getCreatedAt))
                .map(Payment::getStatus)
                .orElse(null);
        return Objects.equals(last, "Failed") || Objects.equals(last, "Cancelled") ? "FAILED" : "PENDING";
    }
}
