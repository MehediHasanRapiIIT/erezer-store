package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
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

    @RequiresPermission(Perm.FLASH_SALES_VIEW)
    @GetMapping
    public ResponseEntity<List<FlashSaleResponseDTO>> list() {
        return ResponseEntity.ok(flashSaleService.list());
    }

    @RequiresPermission(Perm.FLASH_SALES_VIEW)
    @GetMapping("/{id}")
    public ResponseEntity<FlashSaleResponseDTO> get(@PathVariable UUID id) {
        return ResponseEntity.ok(flashSaleService.get(id));
    }

    @RequiresPermission(Perm.FLASH_SALES_CREATE)
    @PostMapping
    public ResponseEntity<FlashSaleResponseDTO> create(@Valid @RequestBody FlashSaleRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(flashSaleService.create(request));
    }

    @RequiresPermission(Perm.FLASH_SALES_EDIT)
    @PutMapping("/{id}")
    public ResponseEntity<FlashSaleResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody FlashSaleRequestDTO request) {
        return ResponseEntity.ok(flashSaleService.update(id, request));
    }

    @RequiresPermission(Perm.FLASH_SALES_DELETE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        flashSaleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
