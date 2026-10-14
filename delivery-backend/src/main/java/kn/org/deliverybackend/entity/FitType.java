package kn.org.deliverybackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One fit in the shop's list (Drop Shoulder, Regular Fit, Oversized…), with the
 * size chart its sizes show. See {@link kn.org.deliverybackend.enumeration.Fit}.
 */
@Entity
@Table(name = "fit")
@Data
@NoArgsConstructor
public class FitType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** As variants store it: DROP_SHOULDER. Made from the first name, and never changed. */
    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "sku_code", nullable = false, length = 6)
    private String skuCode;

    @Column(name = "size_chart_id")
    private Long sizeChartId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private Boolean hidden = Boolean.FALSE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
