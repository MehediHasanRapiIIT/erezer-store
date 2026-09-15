package kn.org.deliverybackend.util;

import kn.org.deliverybackend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Order numbers customers read and type: "EZ-" and six random digits. Random,
 * not counting up, so a number doesn't reveal how many orders the shop gets.
 */
@Component
@RequiredArgsConstructor
public class OrderNumbers {

    public static final String PREFIX = "EZ-";
    private static final Pattern TYPED = Pattern.compile("^\\s*#?\\s*(?:EZ\\s*-?\\s*)?(\\d{6})\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final OrderRepository orderRepository;
    private final SecureRandom random = new SecureRandom();

    /** A number no order has yet. */
    public String next() {
        for (int attempt = 0; attempt < 50; attempt++) {
            String candidate = PREFIX + (100_000 + random.nextInt(900_000));
            if (!orderRepository.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find a free order number");
    }

    /**
     * The number as stored, from what a customer typed: "EZ-482915", "ez482915",
     * "#482915" and "482915" all mean EZ-482915. Empty when it can't be an order number.
     */
    public static Optional<String> normalize(String typed) {
        if (typed == null) return Optional.empty();
        Matcher m = TYPED.matcher(typed.toUpperCase(Locale.ROOT));
        return m.matches() ? Optional.of(PREFIX + m.group(1)) : Optional.empty();
    }
}
