package kn.org.deliverybackend.dto;

import kn.org.deliverybackend.enumeration.BannerSlot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * What one home-page band holds, for the admin Banners page's map of spots:
 * how many banners are in it, and the first two in display order (enough to
 * name the one in use even while that spot's banner is being edited).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BannerSlotSummaryDTO {
    private BannerSlot slot;
    private long count;
    private List<PromotionalBannerDTO> first;
}
