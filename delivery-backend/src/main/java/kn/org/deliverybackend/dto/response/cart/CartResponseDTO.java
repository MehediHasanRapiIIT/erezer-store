package kn.org.deliverybackend.dto.response.cart;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

//Full cart response with totals
public class CartResponseDTO {

    private List<CartItemDTO> items;

    private BigDecimal cartSubtotal;   // sum of all lineTotals

    private BigDecimal cartGrandTotal; // equals subtotal at this stage — delivery added at checkout
}