package kn.org.deliverybackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.dto.returns.ReturnRequestCreateDTO;
import kn.org.deliverybackend.dto.returns.ReturnRequestDTO;
import kn.org.deliverybackend.exception.ForbiddenAccessException;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.service.ReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/app/consumer/{userId}/returns")
@RequiredArgsConstructor
@Tag(name = "Customer Returns")
public class ReturnController {

    private final ReturnService returnService;
    private final ObjectMapper objectMapper;

    /**
     * Multipart endpoint:
     *   - {@code body}: JSON {@link ReturnRequestCreateDTO}
     *   - {@code photos}: 0-3 image parts
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReturnRequestDTO> create(
            @PathVariable UUID userId,
            @RequestParam("orderId") UUID orderId,
            @RequestPart("body") String body,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        assertSelf(userId);
        ReturnRequestCreateDTO request;
        try {
            request = objectMapper.readValue(body, ReturnRequestCreateDTO.class);
        } catch (Exception ex) {
            throw new InvalidStockOperationException("Malformed 'body' JSON: " + ex.getMessage());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(
                returnService.requestReturn(userId, orderId, request,
                        photos == null ? Collections.emptyList() : photos));
    }

    @GetMapping
    public ResponseEntity<List<ReturnRequestDTO>> listMine(@PathVariable UUID userId) {
        assertSelf(userId);
        return ResponseEntity.ok(returnService.listMine(userId));
    }

    @GetMapping("/{returnId}")
    public ResponseEntity<ReturnRequestDTO> getMine(
            @PathVariable UUID userId,
            @PathVariable UUID returnId) {
        assertSelf(userId);
        return ResponseEntity.ok(returnService.getMine(userId, returnId));
    }

    /**
     * Ensures the {@code {userId}} in the path matches the authenticated customer
     * (the JWT subject set by {@code CustomerJwtAuthFilter}) — so a signed-in user
     * can't read or create returns under someone else's id.
     */
    private void assertSelf(UUID userId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String principal = auth != null ? String.valueOf(auth.getPrincipal()) : null;
        if (principal == null || !principal.equals(userId.toString())) {
            throw new ForbiddenAccessException("You can only access your own returns.");
        }
    }
}
