package kn.org.deliverybackend.dto.shipping;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingZoneDTO {
    private Long id;
    private String code;
    private String displayName;
    private String countryCode;
    private BigDecimal flatFee;
    private BigDecimal freeAbove;
    private Boolean isDefault;
    private Boolean isActive;
}
