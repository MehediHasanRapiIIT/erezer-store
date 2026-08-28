package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "promotional_banner")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PromotionalBanner extends AbstractBaseEntity<UUID> {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    private String imageUrl;

    private LocalDate fromDate;

    private LocalDate toDate;

    private String promotionTitle;

    private String promotionDetails;
}
