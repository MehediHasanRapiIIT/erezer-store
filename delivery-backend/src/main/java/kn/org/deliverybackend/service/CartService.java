package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.request.cart.AddToCartRequestDTO;
import kn.org.deliverybackend.dto.response.cart.CartItemDTO;
import kn.org.deliverybackend.dto.response.cart.CartResponseDTO;

import java.util.List;
import java.util.UUID;

public interface CartService {

    // Add product to cart; increments quantity if product already exists
    CartItemDTO addToCart(UUID userId, AddToCartRequestDTO request);

    // Fetch all active cart items with computed subtotal and grand total
    CartResponseDTO getCart(UUID userId);

    // Increment item quantity by 1 (FR-07-03)
    CartItemDTO incrementQuantity(UUID userId, UUID cartItemId);

    // Decrement item quantity by 1; removes item if quantity reaches 0 (FR-07-04)
    CartItemDTO decrementQuantity(UUID userId, UUID cartItemId);

    // Remove a cart item via swipe-to-delete (FR-07-14)
    void removeCartItem(UUID userId, UUID cartItemId);

    // Return total item count for bottom navigation badge (FR-07-12)
    long getCartCount(UUID userId);

    // Merge locally stored guest cart items into user account cart after OTP login (FR-07-10)
    CartResponseDTO mergeGuestCart(UUID userId, List<AddToCartRequestDTO> guestItems);

    // Clear all cart items for a user — called after successful order placement
    void clearCart(UUID userId);
}