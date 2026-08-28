package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.discount.DiscountRequestDTO;
import kn.org.deliverybackend.dto.discount.DiscountResponseDTO;
import kn.org.deliverybackend.service.DiscountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/discounts")
@RequiredArgsConstructor
@Tag(name = "Admin: Discounts")
public class DiscountAdminController {

    private final DiscountService discountService;

    @GetMapping
    public ResponseEntity<List<DiscountResponseDTO>> list() {
        return ResponseEntity.ok(discountService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DiscountResponseDTO> get(@PathVariable UUID id) {
        return ResponseEntity.ok(discountService.get(id));
    }

    @PostMapping
    public ResponseEntity<DiscountResponseDTO> create(@Valid @RequestBody DiscountRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(discountService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DiscountResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody DiscountRequestDTO request) {
        return ResponseEntity.ok(discountService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        discountService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
