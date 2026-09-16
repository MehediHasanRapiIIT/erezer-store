package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.customdesign.CustomDesignAssetsDTO;
import kn.org.deliverybackend.dto.customdesign.CustomDesignDraftDTO;
import kn.org.deliverybackend.dto.customdesign.CustomOrderDTO;
import kn.org.deliverybackend.dto.customdesign.CustomOrderRequestDTO;
import kn.org.deliverybackend.service.CustomDesignService;
import kn.org.deliverybackend.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Public storefront custom-design endpoints: studio assets, artwork upload,
 * "Submit for Price" quote requests, and public shared-design lookup. No auth
 * required — a guest can design and submit a request.
 */
@RestController
@RequestMapping("/api/custom-design")
@RequiredArgsConstructor
@Tag(name = "Custom Design (public)")
public class CustomDesignController {

    private final CustomDesignService customDesignService;
    private final RateLimiterService rateLimiter;

    @GetMapping("/assets")
    public ResponseEntity<CustomDesignAssetsDTO> assets() {
        return ResponseEntity.ok(customDesignService.getAssets());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(@RequestPart("file") MultipartFile file,
                                                       HttpServletRequest http) {
        enforceRateLimit(http);
        String url = customDesignService.uploadArtwork(file);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @PostMapping(value = "/requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CustomOrderDTO> submit(
            @RequestPart("data") @Valid CustomOrderRequestDTO data,
            @RequestPart(value = "previews", required = false) List<MultipartFile> previews,
            HttpServletRequest http) {
        enforceRateLimit(http);
        // One picture per side of the garment; each is also emailed to the shop.
        if (previews != null && previews.size() > 8) {
            throw new kn.org.deliverybackend.exception.InvalidRequestException("Up to 8 preview images can be sent.");
        }
        CustomOrderDTO created = customDesignService.submitRequest(null, data, previews);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/shared/{token}")
    public ResponseEntity<CustomDesignDraftDTO> shared(@PathVariable String token) {
        return ResponseEntity.ok(customDesignService.getSharedDraft(token));
    }

    private void enforceRateLimit(HttpServletRequest http) {
        rateLimiter.enforceAuth("customdesign:" + clientIp(http));
    }

    private String clientIp(HttpServletRequest http) {
        return kn.org.deliverybackend.util.ClientIp.of(http);
    }
}
