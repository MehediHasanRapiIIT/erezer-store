package kn.org.deliverybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRiderDTO {

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

    private LocalDateTime approvedAt;
}

