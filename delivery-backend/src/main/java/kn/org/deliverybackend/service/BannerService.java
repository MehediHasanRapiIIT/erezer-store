package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.BannerSlotSummaryDTO;
import kn.org.deliverybackend.dto.PromotionalBannerDTO;
import kn.org.deliverybackend.dto.request.banner.BannerContentDTO;
import kn.org.deliverybackend.enumeration.BannerSlot;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface BannerService {

    PromotionalBannerDTO uploadBanner(MultipartFile image, BannerContentDTO content);

    List<PromotionalBannerDTO> getAllBanners();

    /** The admin Banners page: one page, optionally one slot and a search, in home-page order. */
    Page<PromotionalBannerDTO> bannerPage(String q, BannerSlot slot, int page, int size);

    /** Every slot with its banner count and first two banners, for the admin map of spots. */
    List<BannerSlotSummaryDTO> slotSummary();

    /**
     * Banners for one home-page band: active on today's date, ordered by
     * sortOrder. A band with no results is expected to hide itself rather than
     * render an empty frame.
     */
    List<PromotionalBannerDTO> getBannersForSlot(BannerSlot slot);

    PromotionalBannerDTO updateBanner(UUID id, MultipartFile image, BannerContentDTO content);

    void deleteBanner(UUID id);
}
