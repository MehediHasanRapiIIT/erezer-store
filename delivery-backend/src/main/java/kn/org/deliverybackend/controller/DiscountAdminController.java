package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.dto.discount.DiscountSwitchesDTO;
import kn.org.deliverybackend.service.StoreSettingsService;
import kn.org.deliverybackend.access.RequiresPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.discount.DiscountRequestDTO;
import kn.org.deliverybackend.dto.discount.DiscountResponseDTO;
import kn.org.deliverybackend.service.DiscountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/discounts")
@RequiredArgsConstructor
@Tag(name = "Admin: Discounts")
public class DiscountAdminController {

    private final DiscountService discountService;
    private final StoreSettingsService storeSettingsService;

    /** The discount on/off switches: the master switch and one per kind of rule. */
    @GetMapping("/switches")
    @RequiresPermission(Perm.DISCOUNTS_VIEW)
    public ResponseEntity<DiscountSwitchesDTO> switches() {
        return ResponseEntity.ok(storeSettingsService.getDiscountSwitches());
    }

    /** Flips one or more switches; a switch left out stays as it is. */
    @PutMapping("/switches")
    @RequiresPermission(Perm.DISCOUNTS_SWITCHES)
    public ResponseEntity<DiscountSwitchesDTO> updateSwitches(@RequestBody DiscountSwitchesDTO change) {
        return ResponseEntity.ok(storeSettingsService.updateDiscountSwitches(change));
    }

    @RequiresPermission(Perm.DISCOUNTS_VIEW)
    @GetMapping
    public ResponseEntity<Page<DiscountResponseDTO>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(discountService.list(q, page, size));
    }

    @RequiresPermission(Perm.DISCOUNTS_VIEW)
    @GetMapping("/{id}")
    public ResponseEntity<DiscountResponseDTO> get(@PathVariable UUID id) {
        return ResponseEntity.ok(discountService.get(id));
    }

    @RequiresPermission(Perm.DISCOUNTS_CREATE)
    @PostMapping
    public ResponseEntity<DiscountResponseDTO> create(@Valid @RequestBody DiscountRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(discountService.create(request));
    }

    @RequiresPermission(Perm.DISCOUNTS_EDIT)
    @PutMapping("/{id}")
    public ResponseEntity<DiscountResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody DiscountRequestDTO request) {
        return ResponseEntity.ok(discountService.update(id, request));
    }

    @RequiresPermission(Perm.DISCOUNTS_DELETE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        discountService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
