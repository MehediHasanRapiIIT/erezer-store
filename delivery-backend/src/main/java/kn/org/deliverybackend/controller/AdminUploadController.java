package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Generic admin image upload. Stores the file via {@link FileStorageService}
 * and returns its public URL — used by store-settings editors (e.g. footer
 * outlet images). Secured by Keycloak (SecurityConfig chain 1 owns
 * {@code /admin/**}).
 */
@RestController
@RequestMapping("/admin/uploads")
@RequiredArgsConstructor
@Tag(name = "Admin: Uploads")
public class AdminUploadController {

    private final FileStorageService fileStorageService;

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadImage(@RequestPart("file") MultipartFile file) {
        String url = fileStorageService.uploadFile(file);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
