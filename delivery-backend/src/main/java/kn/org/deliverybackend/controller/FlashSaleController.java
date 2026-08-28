package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.dto.flashsale.FlashSaleResponseDTO;
import kn.org.deliverybackend.service.FlashSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Public, read-only flash-sale endpoints for the storefront. Anonymous — see
 * SecurityConfig chain 2.
 *
 * <ul>
 *   <li>{@code GET /api/flash-sale}        — the featured sale for the landing widget (204 if none).</li>
 *   <li>{@code GET /api/flash-sales}       — all active sales for the "view all deals" list page.</li>
 *   <li>{@code GET /api/flash-sales/{id}}  — one active sale for its detail page (404 if missing).</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Flash Sale")
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    @GetMapping("/api/flash-sale")
    public ResponseEntity<FlashSaleResponseDTO> featured() {
        FlashSaleResponseDTO sale = flashSaleService.getFeaturedPublic();
        return sale == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(sale);
    }

    @GetMapping("/api/flash-sales")
    public ResponseEntity<List<FlashSaleResponseDTO>> list() {
        return ResponseEntity.ok(flashSaleService.listActivePublic());
    }

    @GetMapping("/api/flash-sales/{id}")
    public ResponseEntity<FlashSaleResponseDTO> byId(@PathVariable UUID id) {
        FlashSaleResponseDTO sale = flashSaleService.getPublicById(id);
        return sale == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(sale);
    }
}
