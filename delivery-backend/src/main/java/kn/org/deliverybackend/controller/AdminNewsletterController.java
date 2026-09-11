package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
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

    @RequiresPermission(Perm.NEWSLETTER_SUBSCRIBERS)
    @GetMapping("/subscribers")
    public ResponseEntity<Page<NewsletterSubscriberDTO>> listSubscribers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(newsletterService.list(status, q, page, size));
    }

    @RequiresPermission(Perm.NEWSLETTER_SUBSCRIBERS)
    @GetMapping("/subscribers/count")
    public ResponseEntity<Long> activeSubscribers() {
        return ResponseEntity.ok(newsletterService.countActiveSubscribers());
    }

    // ── campaigns ──────────────────────────────────────────────────────────────

    @RequiresPermission(Perm.NEWSLETTER_CAMPAIGNS_VIEW)
    @GetMapping("/campaigns")
    public ResponseEntity<Page<NewsletterCampaignDTO>> listCampaigns(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(campaignService.list(q, page, size));
    }

    @RequiresPermission(Perm.NEWSLETTER_CAMPAIGNS_VIEW)
    @GetMapping("/campaigns/{id}")
    public ResponseEntity<NewsletterCampaignDTO> getCampaign(@PathVariable UUID id) {
        return ResponseEntity.ok(campaignService.get(id));
    }

    @RequiresPermission(Perm.NEWSLETTER_CAMPAIGNS_EDIT)
    @PostMapping("/campaigns")
    public ResponseEntity<NewsletterCampaignDTO> createDraft(
            @Valid @RequestBody NewsletterCampaignRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.createDraft(request));
    }

    @RequiresPermission(Perm.NEWSLETTER_CAMPAIGNS_EDIT)
    @PutMapping("/campaigns/{id}")
    public ResponseEntity<NewsletterCampaignDTO> updateDraft(
            @PathVariable UUID id,
            @Valid @RequestBody NewsletterCampaignRequestDTO request) {
        return ResponseEntity.ok(campaignService.update(id, request));
    }

    @RequiresPermission(Perm.NEWSLETTER_CAMPAIGNS_SEND)
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

    @RequiresPermission(Perm.NEWSLETTER_CAMPAIGNS_EDIT)
    @DeleteMapping("/campaigns/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        campaignService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
