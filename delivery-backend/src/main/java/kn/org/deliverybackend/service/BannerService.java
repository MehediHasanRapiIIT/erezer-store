package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.PromotionalBannerDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface BannerService {
    PromotionalBannerDTO uploadBanner(MultipartFile image, String promotionTitle, String promotionDetails, String fromDate, String toDate);
    List<PromotionalBannerDTO> getAllBanners();
    PromotionalBannerDTO updateBanner(UUID id, MultipartFile image, String promotionTitle, String promotionDetails, String fromDate, String toDate);
    void deleteBanner(UUID id);
}
