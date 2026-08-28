package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A blank garment the customer can design on (T-Shirt, Hoodie, Tank Top…).
 * Admin-managed: sizes and print techniques are per-garment lists, and each
 * garment carries one or more {@link CustomDesignColor}s (each with front/back/
 * sleeve mockup images used as the design-canvas background).
 */
@Entity
@Table(name = "custom_design_item",
        indexes = {
                @Index(name = "idx_cd_item_active", columnList = "active, sort_order")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomDesignItem extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    /** Free-text grouping shown in the studio (e.g. "Men", "Women", "Accessories"). */
    @Column(length = 100)
    private String category;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = Boolean.TRUE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "custom_design_item_size",
            joinColumns = @JoinColumn(name = "item_id"))
    @Column(name = "size", length = 40)
    @OrderColumn(name = "position")
    @Builder.Default
    private List<String> sizes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "custom_design_item_print_technique",
            joinColumns = @JoinColumn(name = "item_id"))
    @Column(name = "technique", length = 80)
    @OrderColumn(name = "position")
    @Builder.Default
    private List<String> printTechniques = new ArrayList<>();

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<CustomDesignColor> colors = new ArrayList<>();

    public void addColor(CustomDesignColor color) {
        color.setItem(this);
        this.colors.add(color);
    }
}
