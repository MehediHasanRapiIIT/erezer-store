package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.contact.ContactMessageDTO;
import kn.org.deliverybackend.dto.contact.ContactStatusUpdateDTO;
import kn.org.deliverybackend.service.ContactMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/support/messages")
@RequiredArgsConstructor
@Tag(name = "Admin: Support Inbox")
public class AdminContactController {

    private final ContactMessageService contactService;

    @GetMapping
    public ResponseEntity<Page<ContactMessageDTO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(contactService.list(status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContactMessageDTO> get(@PathVariable UUID id) {
        return ResponseEntity.ok(contactService.get(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ContactMessageDTO> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ContactStatusUpdateDTO update) {
        return ResponseEntity.ok(contactService.updateStatus(id, update));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        contactService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
