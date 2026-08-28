package kn.org.deliverybackend.dto.flashsale;

import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Flash-sale view returned to both the admin panel (full list / edit) and the
 * public storefront endpoint. The storefront ignores {@code isActive}; it only
 * ever receives the single active campaign.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleResponseDTO {
    private UUID id;
    private String name;
    private String label;
    private String discountType;
    private BigDecimal discountValue;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private String couponCode;
    private BigDecimal minSpend;
    private Boolean isActive;
    private Boolean featured;
    private List<ProductResponseDTO> products;
}
