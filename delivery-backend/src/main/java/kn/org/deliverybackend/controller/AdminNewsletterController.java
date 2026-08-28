package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.newsletter.NewsletterCampaignDTO;
import kn.org.deliverybackend.dto.newsletter.NewsletterCampaignRequestDTO;
import kn.org.deliverybackend.dto.newsletter.NewsletterSubscriberDTO;
import kn.org.deliverybackend.service.NewsletterCampaignService;
import kn.org.deliverybackend.service.NewsletterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/newsletter")
@RequiredArgsConstructor
@Tag(name = "Admin: Newsletter")
public class AdminNewsletterController {

    private final NewsletterService newsletterService;
    private final NewsletterCampaignService campaignService;

    // ── subscribers ────────────────────────────────────────────────────────────

    @GetMapping("/subscribers")
    public ResponseEntity<Page<NewsletterSubscriberDTO>> listSubscribers(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(newsletterService.list(status, page, size));
    }

    @GetMapping("/subscribers/count")
    public ResponseEntity<Long> activeSubscribers() {
        return ResponseEntity.ok(newsletterService.countActiveSubscribers());
    }

    // ── campaigns ──────────────────────────────────────────────────────────────

    @GetMapping("/campaigns")
    public ResponseEntity<Page<NewsletterCampaignDTO>> listCampaigns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(campaignService.list(page, size));
    }

    @GetMapping("/campaigns/{id}")
    public ResponseEntity<NewsletterCampaignDTO> getCampaign(@PathVariable UUID id) {
        return ResponseEntity.ok(campaignService.get(id));
    }

    @PostMapping("/campaigns")
    public ResponseEntity<NewsletterCampaignDTO> createDraft(
            @Valid @RequestBody NewsletterCampaignRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.createDraft(request));
    }

    @PutMapping("/campaigns/{id}")
    public ResponseEntity<NewsletterCampaignDTO> updateDraft(
            @PathVariable UUID id,
            @Valid @RequestBody NewsletterCampaignRequestDTO request) {
        return ResponseEntity.ok(campaignService.update(id, request));
    }

    @PostMapping("/campaigns/{id}/send")
    public ResponseEntity<NewsletterCampaignDTO> send(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        String identity = jwt == null ? "admin"
                : (jwt.getClaimAsString("preferred_username") != null
                        ? "admin:" + jwt.getClaimAsString("preferred_username")
                        : "admin:" + jwt.getSubject());
        return ResponseEntity.accepted().body(campaignService.send(id, identity));
    }

    @DeleteMapping("/campaigns/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        campaignService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
