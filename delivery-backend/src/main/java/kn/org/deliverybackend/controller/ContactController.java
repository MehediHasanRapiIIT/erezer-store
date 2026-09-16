package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.auth.MessageResponseDTO;
import kn.org.deliverybackend.dto.contact.ContactMessageRequestDTO;
import kn.org.deliverybackend.service.ContactMessageService;
import kn.org.deliverybackend.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
@Tag(name = "Support (public)")
public class ContactController {

    private final ContactMessageService contactService;
    private final RateLimiterService rateLimiter;

    @PostMapping("/contact")
    public ResponseEntity<MessageResponseDTO> submit(
            @Valid @RequestBody ContactMessageRequestDTO request,
            HttpServletRequest http) {
        // enforceAuth, not tryAcquireAuth + ResponseStatusException: the global
        // handler's catch-all turned that exception into a 500 "Internal server
        // error", whereas RateLimitExceededException has its own 429 handler.
        // Each message emails the shop, so keep it to a real person's pace.
        rateLimiter.enforce("support:" + clientIp(http), 5, java.time.Duration.ofMinutes(10));
        contactService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MessageResponseDTO.of("Thanks — we'll get back to you soon."));
    }

    private String clientIp(HttpServletRequest http) {
        return kn.org.deliverybackend.util.ClientIp.of(http);
    }
}
