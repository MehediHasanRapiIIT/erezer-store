package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.returns.ReturnDecisionDTO;
import kn.org.deliverybackend.dto.returns.ReturnRequestDTO;
import kn.org.deliverybackend.service.ReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/returns")
@RequiredArgsConstructor
@Tag(name = "Admin: Returns")
public class AdminReturnController {

    private final ReturnService returnService;

    @GetMapping
    public ResponseEntity<Page<ReturnRequestDTO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(returnService.listForAdmin(status, page, size));
    }

    @GetMapping("/{returnId}")
    public ResponseEntity<ReturnRequestDTO> get(@PathVariable UUID returnId) {
        return ResponseEntity.ok(returnService.getForAdmin(returnId));
    }

    @PostMapping("/{returnId}/approve")
    public ResponseEntity<ReturnRequestDTO> approve(
            @PathVariable UUID returnId,
            @Valid @RequestBody(required = false) ReturnDecisionDTO decision,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(returnService.approve(returnId, decision, identityOf(jwt)));
    }

    @PostMapping("/{returnId}/reject")
    public ResponseEntity<ReturnRequestDTO> reject(
            @PathVariable UUID returnId,
            @Valid @RequestBody(required = false) ReturnDecisionDTO decision,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(returnService.reject(returnId, decision, identityOf(jwt)));
    }

    @PostMapping("/{returnId}/picked-up")
    public ResponseEntity<ReturnRequestDTO> markPickedUp(
            @PathVariable UUID returnId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(returnService.markPickedUp(returnId, identityOf(jwt)));
    }

    @PostMapping("/{returnId}/refund")
    public ResponseEntity<ReturnRequestDTO> markRefunded(
            @PathVariable UUID returnId,
            @Valid @RequestBody(required = false) ReturnDecisionDTO decision,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(returnService.markRefunded(returnId, decision, identityOf(jwt)));
    }

    private String identityOf(Jwt jwt) {
        if (jwt == null) return "admin";
        String username = jwt.getClaimAsString("preferred_username");
        return "admin:" + (username != null ? username : jwt.getSubject());
    }
}
