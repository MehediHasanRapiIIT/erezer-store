package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * One size chart in the shop's library. A product can name one, a category can
 * name one for everything in and under it, and the default covers the rest.
 */
@Entity
@Table(name = "size_chart")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class SizeChart extends AbstractBaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** What staff call it: "Men's T-Shirt", "Kids Hoodie". Customers don't see the name. */
    @Column(nullable = false, length = 120)
    private String name;

    /** The chart itself, as a {@code SizeChartDTO} in JSON: columns, and a row of measurements per size. */
    @Column(name = "chart_json", nullable = false, columnDefinition = "text")
    private String chartJson;

    /** The chart a product shows when neither it nor any category above it names one. One at most. */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;
}
