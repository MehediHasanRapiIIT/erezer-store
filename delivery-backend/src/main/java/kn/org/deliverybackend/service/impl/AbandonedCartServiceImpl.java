package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.Cart;
import kn.org.deliverybackend.entity.Users;
import kn.org.deliverybackend.repository.CartRepository;
import kn.org.deliverybackend.repository.UsersRepository;
import kn.org.deliverybackend.service.AbandonedCartService;
import kn.org.deliverybackend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AbandonedCartServiceImpl implements AbandonedCartService {

    private final CartRepository cartRepository;
    private final UsersRepository usersRepository;
    private final EmailService emailService;

    @Value("${app.cart.abandon.idle-hours:24}")
    private int idleHours;

    @Value("${app.frontend.store-url}")
    private String storeUrl;

    /**
     * Scheduled scan. Cron is configurable; defaults to once a day at 09:00
     * server local time. Quiet by default — only logs when it finds work.
     */
    @Scheduled(cron = "${app.cart.abandon.cron:0 0 9 * * *}")
    @Override
    public int sweepOnce() {
        Date idleBefore = Date.from(
                LocalDateTime.now().minusHours(idleHours).atZone(ZoneId.systemDefault()).toInstant());

        List<UUID> staleUsers = cartRepository.findUsersWithStaleCart(idleBefore);
        if (staleUsers.isEmpty()) {
            log.debug("Abandoned-cart sweep: nothing to do");
            return 0;
        }

        int emailed = 0;
        for (UUID userId : staleUsers) {
            try {
                if (emailOneUser(userId)) {
                    emailed++;
                }
            } catch (Exception ex) {
                // One bad user shouldn't poison the rest of the sweep.
                log.warn("Abandoned-cart send failed for user {}: {}", userId, ex.getMessage());
            }
        }
        log.info("Abandoned-cart sweep: emailed {} of {} stale users (idle >= {}h)",
                emailed, staleUsers.size(), idleHours);
        return emailed;
    }

    /**
     * Runs each user in its own short transaction so a single failure doesn't
     * roll back updates for the other users in the same sweep.
     */
    @Transactional
    public boolean emailOneUser(UUID userId) {
        Users user = usersRepository.findById(userId).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return false;
        }

        List<Cart> rows = cartRepository.findByUserId(userId);
        if (rows.isEmpty()) {
            return false;
        }

        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Cart c : rows) {
            BigDecimal unit = c.getUnitPrice() != null ? c.getUnitPrice() : BigDecimal.ZERO;
            int qty = c.getQuantity() != null ? c.getQuantity() : 0;
            BigDecimal line = unit.multiply(BigDecimal.valueOf(qty));
            Map<String, Object> row = new HashMap<>();
            row.put("name",      c.getProductName() != null ? c.getProductName() : "Item");
            row.put("quantity",  qty);
            row.put("lineTotal", currency.format(line));
            items.add(row);
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", user.getFirstName() != null ? user.getFirstName() : "there");
        vars.put("items", items);
        vars.put("cartUrl", storeUrl + "/cart");

        emailService.send(user.getEmail(),
                "Your Erezer cart is still waiting",
                "cart-abandoned",
                vars);

        // Stamp every row so the same cart state isn't re-emailed tomorrow.
        LocalDateTime now = LocalDateTime.now();
        for (Cart c : rows) {
            c.setLastEmailedAt(now);
        }
        cartRepository.saveAll(rows);
        return true;
    }
}
