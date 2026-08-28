package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "tax_rule")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxRule extends AbstractBaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    /** Null = applies to every zone (global default). */
    @Column(name = "zone_id")
    private Long zoneId;

    /** Fraction; 0.0500 = 5%. */
    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal rate;

    /** If true the tax is already included in the listed price (no add-on). */
    @Column(name = "is_inclusive", nullable = false)
    private Boolean isInclusive;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
