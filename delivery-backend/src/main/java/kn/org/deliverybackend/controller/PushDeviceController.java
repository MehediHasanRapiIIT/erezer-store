package kn.org.deliverybackend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kn.org.deliverybackend.entity.PushDevice;
import kn.org.deliverybackend.repository.PushDeviceRepository;
import kn.org.deliverybackend.service.RateLimiterService;
import kn.org.deliverybackend.util.ClientIp;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The Erezer app tells the shop which phone it runs on (its Firebase token), so
 * order updates can reach it. Anyone may register a phone; only the customer
 * themself (their own /app/consumer path) can tie it to their account.
 */
@RestController
@RequiredArgsConstructor
public class PushDeviceController {

    private final PushDeviceRepository devices;
    private final RateLimiterService rateLimiter;

    /** A phone with the app; keeps whoever is logged in on it. */
    @PostMapping("/api/push/devices")
    @Transactional
    public ResponseEntity<Void> register(@Valid @RequestBody DeviceRequest request, HttpServletRequest http) {
        rateLimiter.enforce("push-device:" + ClientIp.of(http), 30, Duration.ofMinutes(10));
        PushDevice device = devices.findById(request.getToken()).orElseGet(() -> new PushDevice(request.getToken()));
        device.setUpdatedAt(LocalDateTime.now());
        devices.save(device);
        return ResponseEntity.noContent().build();
    }

    /** The customer logged in on this phone: their orders' updates come here too. */
    @PostMapping("/app/consumer/{userId}/push-devices")
    @Transactional
    public ResponseEntity<Void> link(@PathVariable UUID userId, @Valid @RequestBody DeviceRequest request) {
        rateLimiter.enforce("push-link:" + userId, 30, Duration.ofMinutes(10));
        PushDevice device = devices.findById(request.getToken()).orElseGet(() -> new PushDevice(request.getToken()));
        device.setUserId(userId);
        device.setUpdatedAt(LocalDateTime.now());
        devices.save(device);
        return ResponseEntity.noContent().build();
    }

    /** The customer logged out on this phone: their updates stop coming here. */
    @PostMapping("/app/consumer/{userId}/push-devices/remove")
    @Transactional
    public ResponseEntity<Void> unlink(@PathVariable UUID userId, @Valid @RequestBody DeviceRequest request) {
        devices.findById(request.getToken())
                .filter(d -> userId.equals(d.getUserId()))
                .ifPresent(d -> {
                    d.setUserId(null);
                    d.setUpdatedAt(LocalDateTime.now());
                    devices.save(d);
                });
        return ResponseEntity.noContent().build();
    }

    @Data
    public static class DeviceRequest {
        @NotBlank
        @Size(max = 512)
        private String token;
    }
}
