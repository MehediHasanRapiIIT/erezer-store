package kn.org.deliverybackend.dto.response.cart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartStockValidationResult {
    private boolean valid;
    private List<StockMismatchItem> mismatches;
}
