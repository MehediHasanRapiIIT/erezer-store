package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.flashsale.FlashSaleRequestDTO;
import kn.org.deliverybackend.dto.flashsale.FlashSaleResponseDTO;
import kn.org.deliverybackend.service.FlashSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/flash-sales")
@RequiredArgsConstructor
@Tag(name = "Admin: Flash Sales")
public class FlashSaleAdminController {

    private final FlashSaleService flashSaleService;

    @GetMapping
    public ResponseEntity<List<FlashSaleResponseDTO>> list() {
        return ResponseEntity.ok(flashSaleService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlashSaleResponseDTO> get(@PathVariable UUID id) {
        return ResponseEntity.ok(flashSaleService.get(id));
    }

    @PostMapping
    public ResponseEntity<FlashSaleResponseDTO> create(@Valid @RequestBody FlashSaleRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(flashSaleService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FlashSaleResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody FlashSaleRequestDTO request) {
        return ResponseEntity.ok(flashSaleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        flashSaleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
