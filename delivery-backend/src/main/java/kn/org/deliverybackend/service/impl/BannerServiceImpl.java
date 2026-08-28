package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.PromotionalBannerDTO;
import kn.org.deliverybackend.entity.PromotionalBanner;
import kn.org.deliverybackend.repository.PromotionalBannerRepository;
import kn.org.deliverybackend.service.BannerService;
import kn.org.deliverybackend.service.BannerStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final PromotionalBannerRepository bannerRepository;
    private final BannerStorageService bannerStorageService;

    @Override
    public PromotionalBannerDTO uploadBanner(MultipartFile image, String promotionTitle, String promotionDetails, String fromDate, String toDate) {
        String imageUrl = bannerStorageService.uploadBanner(image);

        PromotionalBanner banner = new PromotionalBanner();
        banner.setImageUrl(imageUrl);
        banner.setPromotionTitle(promotionTitle);
        banner.setPromotionDetails(promotionDetails);
        banner.setFromDate(fromDate != null ? LocalDate.parse(fromDate) : null);
        banner.setToDate(toDate != null ? LocalDate.parse(toDate) : null);

        return toDTO(bannerRepository.save(banner));
    }

    @Override
    public List<PromotionalBannerDTO> getAllBanners() {
        return bannerRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public PromotionalBannerDTO updateBanner(UUID id, MultipartFile image, String promotionTitle, String promotionDetails, String fromDate, String toDate) {
        PromotionalBanner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner not found with id: " + id));

        // Replace image only if a new one is provided
        if (image != null && !image.isEmpty()) {
            if (banner.getImageUrl() != null) {
                String fileName = banner.getImageUrl().substring(banner.getImageUrl().lastIndexOf("/") + 1);
                bannerStorageService.deleteBanner(fileName);
            }
            banner.setImageUrl(bannerStorageService.uploadBanner(image));
        }

        if (promotionTitle != null) banner.setPromotionTitle(promotionTitle);
        if (promotionDetails != null) banner.setPromotionDetails(promotionDetails);
        if (fromDate != null) banner.setFromDate(LocalDate.parse(fromDate));
        if (toDate != null) banner.setToDate(LocalDate.parse(toDate));

        return toDTO(bannerRepository.save(banner));
    }

    @Override
    public void deleteBanner(UUID id) {
        PromotionalBanner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner not found with id: " + id));

        String imageUrl = banner.getImageUrl();
        if (imageUrl != null) {
            String fileName = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
            bannerStorageService.deleteBanner(fileName);
        }

        bannerRepository.deleteById(id);
    }

    private PromotionalBannerDTO toDTO(PromotionalBanner banner) {
        return new PromotionalBannerDTO(
                banner.getId(),
                banner.getImageUrl(),
                banner.getFromDate(),
                banner.getToDate(),
                banner.getPromotionTitle(),
                banner.getPromotionDetails()
        );
    }
}
