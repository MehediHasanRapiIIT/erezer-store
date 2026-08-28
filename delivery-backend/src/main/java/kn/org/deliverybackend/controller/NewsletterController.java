package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.auth.MessageResponseDTO;
import kn.org.deliverybackend.dto.newsletter.NewsletterSubscribeRequestDTO;
import kn.org.deliverybackend.service.NewsletterService;
import kn.org.deliverybackend.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/newsletter")
@RequiredArgsConstructor
@Tag(name = "Newsletter (public)")
public class NewsletterController {

    private final NewsletterService newsletterService;
    private final RateLimiterService rateLimiter;

    @PostMapping("/subscribe")
    public ResponseEntity<MessageResponseDTO> subscribe(
            @Valid @RequestBody NewsletterSubscribeRequestDTO request,
            HttpServletRequest http) {
        if (!rateLimiter.tryAcquireAuth("newsletter:" + clientIp(http))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts from this address. Please try again later.");
        }
        newsletterService.subscribe(request);
        // Always return a generic success — never leak whether the email already existed.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MessageResponseDTO.of("Thanks — you're on the list."));
    }

    /**
     * Token-based unsubscribe. Idempotent and quiet — always returns 200 so a
     * crawler clicking the link can't enumerate subscribers.
     */
    @GetMapping("/unsubscribe")
    public ResponseEntity<MessageResponseDTO> unsubscribe(@RequestParam String token) {
        newsletterService.unsubscribeByToken(token);
        return ResponseEntity.ok(MessageResponseDTO.of("You've been unsubscribed."));
    }

    private String clientIp(HttpServletRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
