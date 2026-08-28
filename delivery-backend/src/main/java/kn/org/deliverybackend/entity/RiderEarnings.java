package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "rider_earnings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RiderEarnings extends AbstractBaseEntity<UUID> {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    private UUID riderId;

    private UUID orderId;

    private BigDecimal earningAmount;
}
