package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.dto.PromotionalBannerDTO;
import kn.org.deliverybackend.dto.request.banner.BannerContentDTO;
import kn.org.deliverybackend.enumeration.BannerSlot;
import kn.org.deliverybackend.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
@CrossOrigin("*")
public class BannerController {

    private final BannerService bannerService;

    @GetMapping
    public ResponseEntity<List<PromotionalBannerDTO>> getAllBanners() {
        return ResponseEntity.ok(bannerService.getAllBanners());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PromotionalBannerDTO> uploadBanner(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "promotionTitle", required = false) String promotionTitle,
            @RequestParam(value = "promotionDetails", required = false) String promotionDetails,
            @RequestParam(value = "fromDate", required = false) String fromDate,
            @RequestParam(value = "toDate", required = false) String toDate,
            // Added with the editorial landing page. All optional, so existing
            // clients that post only the original fields keep working.
            @RequestParam(value = "slot", required = false) String slot,
            @RequestParam(value = "ctaLabel", required = false) String ctaLabel,
            @RequestParam(value = "ctaLink", required = false) String ctaLink,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder) {
        return ResponseEntity.ok(bannerService.uploadBanner(
                image, content(promotionTitle, promotionDetails, fromDate, toDate,
                        slot, ctaLabel, ctaLink, sortOrder)));
    }

    /** Active banners for one home-page band, in display order. */
    @GetMapping("/slot/{slot}")
    public ResponseEntity<List<PromotionalBannerDTO>> getBannersForSlot(@PathVariable String slot) {
        return BannerSlot.parse(slot)
                .map(s -> ResponseEntity.ok(bannerService.getBannersForSlot(s)))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PromotionalBannerDTO> updateBanner(
            @PathVariable UUID id,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "promotionTitle", required = false) String promotionTitle,
            @RequestParam(value = "promotionDetails", required = false) String promotionDetails,
            @RequestParam(value = "fromDate", required = false) String fromDate,
            @RequestParam(value = "toDate", required = false) String toDate,
            @RequestParam(value = "slot", required = false) String slot,
            @RequestParam(value = "ctaLabel", required = false) String ctaLabel,
            @RequestParam(value = "ctaLink", required = false) String ctaLink,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder) {
        return ResponseEntity.ok(bannerService.updateBanner(
                id, image, content(promotionTitle, promotionDetails, fromDate, toDate,
                        slot, ctaLabel, ctaLink, sortOrder)));
    }

    /**
     * Assembles the loose multipart params into one object. Dates are parsed
     * here so a malformed value fails at the edge with a clear 400 rather than
     * deep inside the service.
     */
    private BannerContentDTO content(String promotionTitle, String promotionDetails,
                                     String fromDate, String toDate, String slot,
                                     String ctaLabel, String ctaLink, Integer sortOrder) {
        return BannerContentDTO.builder()
                .promotionTitle(promotionTitle)
                .promotionDetails(promotionDetails)
                .fromDate(parseDate(fromDate, "fromDate"))
                .toDate(parseDate(toDate, "toDate"))
                .slot(BannerSlot.parse(slot).orElse(null))
                .ctaLabel(ctaLabel)
                .ctaLink(ctaLink)
                .sortOrder(sortOrder)
                .build();
    }

    private LocalDate parseDate(String raw, String field) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(field + " must be an ISO date (yyyy-MM-dd), got: " + raw);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBanner(@PathVariable UUID id) {
        bannerService.deleteBanner(id);
        return ResponseEntity.noContent().build();
    }
}
