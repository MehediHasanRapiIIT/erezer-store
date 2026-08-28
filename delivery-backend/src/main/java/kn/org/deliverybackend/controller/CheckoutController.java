package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.checkout.CheckoutQuoteRequestDTO;
import kn.org.deliverybackend.dto.checkout.CheckoutQuoteResponseDTO;
import kn.org.deliverybackend.service.CheckoutQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
@Tag(name = "Checkout (public)")
public class CheckoutController {

    private final CheckoutQuoteService quoteService;

    /**
     * Returns the full price breakdown for a candidate order — used by the
     * cart and checkout pages to display "what will I pay?" before submit.
     */
    @PostMapping("/quote")
    public ResponseEntity<CheckoutQuoteResponseDTO> quote(
            @Valid @RequestBody CheckoutQuoteRequestDTO request) {
        return ResponseEntity.ok(quoteService.quote(request));
    }
}
