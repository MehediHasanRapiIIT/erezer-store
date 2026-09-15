package kn.org.deliverybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {
    private UUID id;

    /** The number the customer sees and tracks by, e.g. EZ-482915. */
    private String orderNumber;

    private UUID clientId;

    private UUID riderId;

    private String deliveryAddress;

    private BigDecimal totalAmount;

    private String paymentMethod;

    private UUID paymentId;

    private String orderStatus;

    private Long shopId;

    private LocalDateTime createdAt;

    private Double deliveryCharge;

    private Float latitude;

    private Float longitude;

    private List<OrderItemDTO> orderItems;

    // Enriched customer info (populated from Users table)
    private String customerName;
    private String customerPhone;
    private String customerEmail;

    // Enriched rider info (populated from UserRider table)
    private String riderName;
    private String riderPhone;
    private String riderImageUrl;
    private String riderVehicleType;
    private String riderPlateNumber;
    private Double riderRating;
}
