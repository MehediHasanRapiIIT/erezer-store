package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.dto.PromotionalBannerDTO;
import kn.org.deliverybackend.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
            @RequestParam(value = "toDate", required = false) String toDate) {
        return ResponseEntity.ok(bannerService.uploadBanner(image, promotionTitle, promotionDetails, fromDate, toDate));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PromotionalBannerDTO> updateBanner(
            @PathVariable UUID id,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "promotionTitle", required = false) String promotionTitle,
            @RequestParam(value = "promotionDetails", required = false) String promotionDetails,
            @RequestParam(value = "fromDate", required = false) String fromDate,
            @RequestParam(value = "toDate", required = false) String toDate) {
        return ResponseEntity.ok(bannerService.updateBanner(id, image, promotionTitle, promotionDetails, fromDate, toDate));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBanner(@PathVariable UUID id) {
        bannerService.deleteBanner(id);
        return ResponseEntity.noContent().build();
    }
}
