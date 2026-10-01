package kn.org.deliverybackend.dto.response.category;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponseDTO {
    private Long id;
    private String name;
    private Boolean isActive;
    private String imageUrl;
    private long productCount;
    private String slug;
    private Boolean showOnHome;
    private Integer homeSortOrder;
    private Boolean discountExcluded;
    private Boolean showStockQuantity;

    /**
     * What every product in this category costs to deliver, unless the product
     * has a charge of its own. Null means the customer's area price decides.
     * Zero means delivered free.
     */
    private BigDecimal shippingCharge;
}
