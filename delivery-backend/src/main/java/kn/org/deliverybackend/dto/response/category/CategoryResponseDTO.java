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

    /** The main category this one sits under; null for a main category. */
    private Long parentId;
    /** That main category's name, for "Hoodies › Zip Hoodies". */
    private String parentName;
    /** Products put directly in this category, not counting its subcategories'. */
    private long ownProductCount;
    /** How many subcategories a main category has. */
    private int subcategoryCount;
    /**
     * For a subcategory, what it takes from its parent when it sets nothing of
     * its own: the delivery charge, "never discount" and showing stock as a
     * number. For a main category these repeat its own values.
     */
    private BigDecimal effectiveShippingCharge;
    private Boolean effectiveDiscountExcluded;
    private Boolean effectiveShowStockQuantity;

    /**
     * What every product in this category costs to deliver, unless the product
     * has a charge of its own. Null means the customer's area price decides.
     * Zero means delivered free.
     */
    private BigDecimal shippingCharge;
}
