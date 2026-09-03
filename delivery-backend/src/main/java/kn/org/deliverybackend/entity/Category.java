package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "category")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Category extends AbstractBaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Boolean isActive;

    /** Optional image shown in the storefront "Shop by category" tiles. */
    private String imageUrl;

    /**
     * URL-safe name for this category's own storefront page, e.g. "erezer-pink"
     * serving /erezer-pink. Unique across categories; derived from the name when
     * the admin leaves it blank.
     */
    @Column(name = "slug", length = 140, unique = true)
    private String slug;

    /**
     * Whether the landing page gives this category its own product section,
     * below Featured products. Lets the admin promote a collection without a
     * code change.
     */
    @Column(name = "show_on_home")
    private Boolean showOnHome;

    /** Ordering among home sections, lowest first. */
    @Column(name = "home_sort_order")
    private Integer homeSortOrder;

    /**
     * True to keep every product in this category at full price: the discount
     * engine ignores every automatic discount for them, including store-wide
     * ones. Null means not excluded.
     */
    @Column(name = "discount_excluded")
    private Boolean discountExcluded;
}
