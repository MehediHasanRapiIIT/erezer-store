package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.ordernote.OrderNoteDTO;
import kn.org.deliverybackend.dto.ordernote.OrderNoteRequestDTO;
import kn.org.deliverybackend.service.OrderNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/orders/{orderId}/notes")
@RequiredArgsConstructor
@Tag(name = "Admin: Order Notes")
public class AdminOrderNoteController {

    private final OrderNoteService orderNoteService;

    @GetMapping
    public ResponseEntity<List<OrderNoteDTO>> list(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderNoteService.list(orderId));
    }

    @PostMapping
    public ResponseEntity<OrderNoteDTO> create(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderNoteRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {
        String author = jwt == null
                ? "admin"
                : (jwt.getClaimAsString("preferred_username") != null
                        ? jwt.getClaimAsString("preferred_username")
                        : jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderNoteService.add(orderId, request, author));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID orderId,
            @PathVariable UUID noteId) {
        orderNoteService.delete(orderId, noteId);
        return ResponseEntity.noContent().build();
    }
}
