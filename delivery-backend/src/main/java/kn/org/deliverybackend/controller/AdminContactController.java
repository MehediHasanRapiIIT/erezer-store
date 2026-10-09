package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
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
    private final kn.org.deliverybackend.service.impl.ContactReplyService replyService;

    @RequiresPermission(Perm.SUPPORT_VIEW)
    @GetMapping
    public ResponseEntity<Page<ContactMessageDTO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ContactMessageDTO> found = contactService.list(status, q, page, size);
        replyService.attachReplies(found.getContent());
        return ResponseEntity.ok(found);
    }

    @RequiresPermission(Perm.SUPPORT_VIEW)
    @GetMapping("/{id}")
    public ResponseEntity<ContactMessageDTO> get(@PathVariable UUID id) {
        return ResponseEntity.ok(withReplies(contactService.get(id)));
    }

    @RequiresPermission(Perm.SUPPORT_UPDATE)
    @PatchMapping("/{id}")
    public ResponseEntity<ContactMessageDTO> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ContactStatusUpdateDTO update) {
        return ResponseEntity.ok(withReplies(contactService.updateStatus(id, update)));
    }

    /**
     * Answers a message: the reply is emailed to the customer from the shop's
     * address and kept under the message, which becomes RESOLVED. Returns the
     * message as it now stands.
     */
    @RequiresPermission(Perm.SUPPORT_UPDATE)
    @PostMapping("/{id}/reply")
    public ResponseEntity<ContactMessageDTO> reply(
            @PathVariable UUID id,
            @Valid @RequestBody kn.org.deliverybackend.dto.contact.ContactReplyDTO reply) {
        var staff = kn.org.deliverybackend.access.StaffAccess.current();
        String name = staff.map(s -> s.name() != null && !s.name().isBlank() ? s.name() : s.username()).orElse(null);
        replyService.reply(id, reply.getBody(), staff.map(kn.org.deliverybackend.access.StaffView::id).orElse(null), name);
        ContactMessageDTO message = withReplies(contactService.get(id));
        kn.org.deliverybackend.access.StaffAccess.describe("Replied to the support message from " + message.getName());
        return ResponseEntity.ok(message);
    }

    private ContactMessageDTO withReplies(ContactMessageDTO message) {
        replyService.attachReplies(java.util.List.of(message));
        return message;
    }

    @RequiresPermission(Perm.SUPPORT_DELETE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        contactService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
