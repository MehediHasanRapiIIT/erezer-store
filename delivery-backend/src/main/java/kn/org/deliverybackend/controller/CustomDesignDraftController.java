package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.customdesign.CustomDesignDraftDTO;
import kn.org.deliverybackend.dto.customdesign.SaveDraftRequestDTO;
import kn.org.deliverybackend.service.CustomDesignService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Saved design drafts for logged-in customers ("save" / "saved draft" / "share
 * design"). Lives on the authenticated {@code /app/consumer} chain.
 */
@RestController
@RequestMapping("/app/consumer/{userId}/custom-design/drafts")
@RequiredArgsConstructor
@Tag(name = "Custom Design Drafts (customer)")
public class CustomDesignDraftController {

    private final CustomDesignService customDesignService;

    @GetMapping
    public ResponseEntity<List<CustomDesignDraftDTO>> list(@PathVariable UUID userId) {
        return ResponseEntity.ok(customDesignService.listDrafts(userId));
    }

    @PostMapping
    public ResponseEntity<CustomDesignDraftDTO> create(@PathVariable UUID userId,
                                                       @Valid @RequestBody SaveDraftRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customDesignService.saveDraft(userId, request));
    }

    @GetMapping("/{draftId}")
    public ResponseEntity<CustomDesignDraftDTO> get(@PathVariable UUID userId, @PathVariable UUID draftId) {
        return ResponseEntity.ok(customDesignService.getDraft(userId, draftId));
    }

    @PutMapping("/{draftId}")
    public ResponseEntity<CustomDesignDraftDTO> update(@PathVariable UUID userId,
                                                       @PathVariable UUID draftId,
                                                       @Valid @RequestBody SaveDraftRequestDTO request) {
        return ResponseEntity.ok(customDesignService.updateDraft(userId, draftId, request));
    }

    @DeleteMapping("/{draftId}")
    public ResponseEntity<Void> delete(@PathVariable UUID userId, @PathVariable UUID draftId) {
        customDesignService.deleteDraft(userId, draftId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{draftId}/share")
    public ResponseEntity<CustomDesignDraftDTO> share(@PathVariable UUID userId, @PathVariable UUID draftId) {
        return ResponseEntity.ok(customDesignService.shareDraft(userId, draftId));
    }
}
