package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.dto.shipping.ShippingZoneDTO;
import kn.org.deliverybackend.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
@Tag(name = "Shipping (public)")
public class ShippingController {

    private final ShippingService shippingService;

    @GetMapping("/zones")
    public ResponseEntity<List<ShippingZoneDTO>> listZones() {
        return ResponseEntity.ok(shippingService.listActive());
    }
}
