package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.cart.AddToCartRequestDTO;
import kn.org.deliverybackend.dto.response.cart.CartItemDTO;
import kn.org.deliverybackend.dto.response.cart.CartResponseDTO;
import kn.org.deliverybackend.entity.Cart;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.enumeration.StockStatus;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.mapper.CartMapper;
import kn.org.deliverybackend.repository.CartRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.UsersRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import kn.org.deliverybackend.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final UsersRepository usersRepository;
    private final ProductRepository productRepository;
    private final VariantRepository variantRepository;
    private final CartMapper cartMapper;

    @Override
    @Transactional
    public CartItemDTO addToCart(UUID userId, AddToCartRequestDTO request) {
        usersRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

        // Resolve the variant (if any) and validate it belongs to this product.
        Long variantId = request.getVariantId();
        Variant variant = null;
        if (variantId != null) {
            variant = variantRepository.findById(variantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + variantId));
            if (!product.getId().equals(variant.getProductId())) {
                throw new IllegalArgumentException(
                        "Variant " + variantId + " does not belong to product " + product.getId());
            }
        }

        // Price and display name are resolved server-side — never trust the client.
        BigDecimal unitPrice = resolveUnitPrice(product, variant);
        String displayName = resolveDisplayName(product, variant);
        int addQty = request.getQuantity() != null && request.getQuantity() > 0 ? request.getQuantity() : 1;

        // Same product+variant increments the existing line; otherwise a new line.
        Cart cart = cartRepository.findByUserIdAndProductIdAndVariantId(userId, product.getId(), variantId)
                .map(existing -> {
                    existing.setQuantity(existing.getQuantity() + addQty);
                    return cartRepository.save(existing);
                })
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setUserId(userId);
                    newCart.setProductId(product.getId());
                    newCart.setVariantId(variantId);
                    newCart.setProductName(displayName);
                    newCart.setImageUrl(product.getImageUrl());
                    newCart.setUnitPrice(unitPrice);
                    newCart.setQuantity(addQty);
                    newCart.setStockStatus(StockStatus.IN_STOCK);
                    return cartRepository.save(newCart);
                });
        return toItemDTO(cart);
    }

    /** Variant price override wins; otherwise the product's discounted/base price. */
    private BigDecimal resolveUnitPrice(Product product, Variant variant) {
        if (variant != null && variant.getPriceOverride() != null) {
            return variant.getPriceOverride();
        }
        return product.getDiscountPrice() != null ? product.getDiscountPrice() : product.getPrice();
    }

    /** Product name, suffixed with the variant label (name, or size/colour) when present. */
    private String resolveDisplayName(Product product, Variant variant) {
        if (variant == null) {
            return product.getName();
        }
        // Variants are size-only — label by size, ignoring any legacy colour
        // that may be baked into the stored variant name.
        String label = variant.getSize();
        if (label == null || label.isBlank()) {
            label = variant.getName();
        }
        return (label == null || label.isBlank())
                ? product.getName()
                : product.getName() + " — " + label;
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponseDTO getCart(UUID userId) {
        usersRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return buildCartResponse(cartRepository.findByUserId(userId));
    }

    @Override
    @Transactional
    public CartItemDTO incrementQuantity(UUID userId, UUID cartItemId) {
        Cart cart = cartRepository.findByIdAndUserId(cartItemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + cartItemId));
        cart.setQuantity(cart.getQuantity() + 1); // FR-07-03
        return toItemDTO(cartRepository.save(cart));
    }

    @Override
    @Transactional
    public CartItemDTO decrementQuantity(UUID userId, UUID cartItemId) {
        Cart cart = cartRepository.findByIdAndUserId(cartItemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + cartItemId));
        if (cart.getQuantity() <= 1) {
            cartRepository.delete(cart);
            return null; // null signals to controller that item was removed → 204
        }
        cart.setQuantity(cart.getQuantity() - 1); // FR-07-04
        return toItemDTO(cartRepository.save(cart));
    }

    @Override
    @Transactional
    public void removeCartItem(UUID userId, UUID cartItemId) {
        Cart cart = cartRepository.findByIdAndUserId(cartItemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + cartItemId));
        cartRepository.delete(cart);
    }

    @Override
    @Transactional(readOnly = true)
    public long getCartCount(UUID userId) {
        usersRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return cartRepository.countByUserId(userId);
    }

    @Override
    @Transactional
    public CartResponseDTO mergeGuestCart(UUID userId, List<AddToCartRequestDTO> guestItems) {
        usersRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        for (AddToCartRequestDTO item : guestItems) {
            // If the same product+variant exists in the user cart, add guest quantity
            // on top; otherwise create a new line.
            cartRepository.findByUserIdAndProductIdAndVariantId(userId, item.getProductId(), item.getVariantId())
                    .ifPresentOrElse(
                            existing -> {
                                existing.setQuantity(existing.getQuantity() + (item.getQuantity() != null && item.getQuantity() > 0 ? item.getQuantity() : 1));
                                cartRepository.save(existing);
                            },
                            () -> {
                                Cart newCart = new Cart();
                                newCart.setUserId(userId);
                                newCart.setProductId(item.getProductId()); // Long
                                newCart.setVariantId(item.getVariantId());
                                newCart.setProductName(item.getProductName());
                                newCart.setImageUrl(item.getImageUrl());
                                newCart.setUnitPrice(item.getUnitPrice());
                                newCart.setQuantity(item.getQuantity() != null && item.getQuantity() > 0 ? item.getQuantity() : 1);
                                newCart.setStockStatus(StockStatus.IN_STOCK);
                                cartRepository.save(newCart);
                            }
                    );
        }
        return buildCartResponse(cartRepository.findByUserId(userId));
    }

    @Override
    @Transactional
    public void clearCart(UUID userId) {
        usersRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        cartRepository.deleteByUserId(userId);
    }

    private CartItemDTO toItemDTO(Cart cart) {
        return cartMapper.toCartItemDTO(cart);
    }

    // Build cart response with item list, subtotal and grand total
    private CartResponseDTO buildCartResponse(List<Cart> cartItems) {
        List<CartItemDTO> itemDTOs = cartItems.stream()
                .map(this::toItemDTO)
                .toList();
        // OOS items shown but excluded from subtotal (FR-07-13)
        BigDecimal subtotal = itemDTOs.stream()
                .filter(i -> i.getStockStatus() != StockStatus.OUT_OF_STOCK)
                .map(i -> i.getLineTotal() != null ? i.getLineTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Grand total equals subtotal at cart stage; delivery fee added at checkout (FR-07-06)
        return CartResponseDTO.builder()
                .items(itemDTOs)
                .cartSubtotal(subtotal)
                .cartGrandTotal(subtotal)
                .build();
    }
}