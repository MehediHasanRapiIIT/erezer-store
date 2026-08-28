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
import org.springframework.web.server.ResponseStatusException;

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
        if (!rateLimiter.tryAcquireAuth("support:" + clientIp(http))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many messages from this address. Please try again later.");
        }
        contactService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MessageResponseDTO.of("Thanks — we'll get back to you soon."));
    }

    private String clientIp(HttpServletRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
