package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import kn.org.deliverybackend.dto.order.PublicOrderTrackingDTO;
import kn.org.deliverybackend.exception.RateLimitExceededException;
import kn.org.deliverybackend.service.PublicOrderTrackingService;
import kn.org.deliverybackend.service.RateLimiterService;
import kn.org.deliverybackend.util.ClientIp;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** The shop's Track Order page: look an order up by its number, no sign-in. */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order tracking (public)")
public class PublicOrderTrackingController {

    /**
     * Generous for real customers, slow for someone trying number after number.
     * The Track Order page re-checks an open order every 5 seconds (12 a minute).
     */
    private static final int LOOKUPS_PER_WINDOW = 60;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final PublicOrderTrackingService trackingService;
    private final RateLimiterService rateLimiter;

    @GetMapping("/track")
    public ResponseEntity<PublicOrderTrackingDTO> track(@RequestParam("number") String number, HttpServletRequest http) {
        if (!rateLimiter.tryAcquire("rl:track:" + ClientIp.of(http), LOOKUPS_PER_WINDOW, WINDOW)) {
            throw new RateLimitExceededException((int) WINDOW.toSeconds(), LOOKUPS_PER_WINDOW, (int) WINDOW.toSeconds());
        }
        return ResponseEntity.ok(trackingService.track(number));
    }
}
