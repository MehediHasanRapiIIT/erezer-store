package kn.org.deliverybackend.mapper;

import kn.org.deliverybackend.dto.response.cart.CartItemDTO;
import kn.org.deliverybackend.entity.Cart;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CartMapper {

    @Mapping(target = "cartItemId", source = "id")
    @Mapping(target = "lineTotal", expression = "java(cart.getUnitPrice() != null && cart.getQuantity() != null ? cart.getUnitPrice().multiply(java.math.BigDecimal.valueOf(cart.getQuantity())) : java.math.BigDecimal.ZERO)")
    CartItemDTO toCartItemDTO(Cart cart);
}
