package kn.org.deliverybackend.controller;

import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.request.cart.AddToCartRequestDTO;
import kn.org.deliverybackend.dto.response.cart.CartItemDTO;
import kn.org.deliverybackend.dto.response.cart.CartResponseDTO;
import kn.org.deliverybackend.dto.response.common.ApiResponse;
import kn.org.deliverybackend.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/app/consumer")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    // Add a product to the cart; increments quantity if the product already exists
    @PostMapping("/{userId}/cart")
    public ResponseEntity<ApiResponse<CartItemDTO>> addToCart(
            @PathVariable UUID userId,
            @Valid @RequestBody AddToCartRequestDTO request) {
        CartItemDTO item = cartService.addToCart(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Item added to cart", item));
    }

    // Retrieve all cart items with total price summary for the given user
    @GetMapping("/{userId}/cart")
    public ResponseEntity<ApiResponse<CartResponseDTO>> getCart(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("Cart retrieved", cartService.getCart(userId)));
    }

    // Increment cart item quantity by 1 (FR-07-03)
    @PatchMapping("/{userId}/cart/{cartItemId}/increment")
    public ResponseEntity<ApiResponse<CartItemDTO>> increment(
            @PathVariable UUID userId,
            @PathVariable UUID cartItemId) {
        return ResponseEntity.ok(ApiResponse.success("Quantity increased", cartService.incrementQuantity(userId, cartItemId)));
    }

    // Decrement cart item quantity by 1; removes item if quantity reaches 0 (FR-07-04)
    @PatchMapping("/{userId}/cart/{cartItemId}/decrement")
    public ResponseEntity<ApiResponse<CartItemDTO>> decrement(
            @PathVariable UUID userId,
            @PathVariable UUID cartItemId) {
        CartItemDTO result = cartService.decrementQuantity(userId, cartItemId);
        if (result == null) {
            return ResponseEntity.ok(ApiResponse.success("Item removed from cart"));
        }
        return ResponseEntity.ok(ApiResponse.success("Quantity decreased", result));
    }

    // Hard remove a cart item via swipe-to-delete gesture (FR-07-14)
    @DeleteMapping("/{userId}/cart/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> removeCartItem(
            @PathVariable UUID userId,
            @PathVariable UUID cartItemId) {
        cartService.removeCartItem(userId, cartItemId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart"));
    }

    // Return total number of items in cart for the bottom navigation badge (FR-07-12)
    @GetMapping("/{userId}/cart/count")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> getCartCount(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("Cart count retrieved",
                java.util.Map.of("count", cartService.getCartCount(userId))));
    }

    // Clear all cart items after successful order placement
    @DeleteMapping("/{userId}/cart")
    public ResponseEntity<ApiResponse<Void>> clearCart(@PathVariable UUID userId) {
        cartService.clearCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Cart cleared"));
    }

    // Merge locally stored guest cart items into user account cart after OTP login (FR-07-10)
    @PostMapping("/{userId}/cart/merge")
    public ResponseEntity<ApiResponse<CartResponseDTO>> mergeGuestCart(
            @PathVariable UUID userId,
            @RequestBody List<AddToCartRequestDTO> guestItems) {
        return ResponseEntity.ok(ApiResponse.success("Guest cart merged", cartService.mergeGuestCart(userId, guestItems)));
    }
}
