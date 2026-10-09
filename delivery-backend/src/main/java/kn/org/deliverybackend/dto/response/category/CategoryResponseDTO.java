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

    /** The category directly above this one; null for a main category. */
    private Long parentId;
    /** That category's name. */
    private String parentName;
    /** How far down it sits: 0 for a main category, 1 for its subcategory, 2 for one under that, and so on. */
    private int depth;
    /** The whole way down to it: "Men › T-Shirts › Drop Shoulder". Just the name for a main category. */
    private String path;
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
    /** The size chart this category names for its products; null for none of its own. */
    private Long sizeChartId;
    /** The chart its products get from it: its own, else that of the nearest category above that names one. */
    private Long effectiveSizeChartId;
}
