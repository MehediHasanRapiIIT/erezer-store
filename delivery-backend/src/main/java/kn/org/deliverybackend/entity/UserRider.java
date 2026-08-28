package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_rider")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRider extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    private String contactNo;

    private String nidNumber;

    private String address;

    private String imageUrl;

    private String drivingLicense;

    private String guardianName;

    private String password;

    private String contactPhone;

    private String status;

    private String vehicleType;

    private String plateNumber;

    private Double rating;

    private LocalDateTime approvedAt;
}
