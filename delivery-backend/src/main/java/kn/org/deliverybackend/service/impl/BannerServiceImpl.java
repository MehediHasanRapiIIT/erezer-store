package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.PromotionalBannerDTO;
import kn.org.deliverybackend.dto.request.banner.BannerContentDTO;
import kn.org.deliverybackend.enumeration.BannerSlot;
import kn.org.deliverybackend.entity.PromotionalBanner;
import kn.org.deliverybackend.repository.PromotionalBannerRepository;
import kn.org.deliverybackend.service.BannerService;
import kn.org.deliverybackend.service.BannerStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final PromotionalBannerRepository bannerRepository;
    private final BannerStorageService bannerStorageService;

    @Override
    public PromotionalBannerDTO uploadBanner(MultipartFile image, BannerContentDTO content) {
        String imageUrl = bannerStorageService.uploadBanner(image);
        BannerContentDTO c = content != null ? content : new BannerContentDTO();

        PromotionalBanner banner = new PromotionalBanner();
        banner.setImageUrl(imageUrl);
        banner.setPromotionTitle(c.getPromotionTitle());
        banner.setPromotionDetails(c.getPromotionDetails());
        banner.setFromDate(c.getFromDate());
        banner.setToDate(c.getToDate());
        // Default to HERO so a banner uploaded without choosing a slot behaves
        // exactly as banners did before slots existed.
        banner.setSlot(c.getSlot() != null ? c.getSlot() : BannerSlot.HERO);
        banner.setCtaLabel(c.getCtaLabel());
        banner.setCtaLink(c.getCtaLink());
        banner.setSortOrder(c.getSortOrder() != null ? c.getSortOrder() : 0);

        return toDTO(bannerRepository.save(banner));
    }

    @Override
    public List<PromotionalBannerDTO> getBannersForSlot(BannerSlot slot) {
        LocalDate today = LocalDate.now();
        return bannerRepository.findAll().stream()
                .filter(b -> !Boolean.TRUE.equals(b.getDeleted()))
                // A null slot is legacy data from before the editorial page, and
                // HERO is the only band those were ever shown in.
                .filter(b -> (b.getSlot() != null ? b.getSlot() : BannerSlot.HERO) == slot)
                .filter(b -> b.getFromDate() == null || !b.getFromDate().isAfter(today))
                .filter(b -> b.getToDate() == null || !b.getToDate().isBefore(today))
                .sorted(Comparator.comparing(
                        b -> b.getSortOrder() != null ? b.getSortOrder() : 0))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<PromotionalBannerDTO> getAllBanners() {
        return bannerRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public PromotionalBannerDTO updateBanner(UUID id, MultipartFile image, BannerContentDTO content) {
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

        // Null means "leave as-is", matching how this endpoint already behaved.
        BannerContentDTO c = content != null ? content : new BannerContentDTO();
        if (c.getPromotionTitle() != null) banner.setPromotionTitle(c.getPromotionTitle());
        if (c.getPromotionDetails() != null) banner.setPromotionDetails(c.getPromotionDetails());
        if (c.getFromDate() != null) banner.setFromDate(c.getFromDate());
        if (c.getToDate() != null) banner.setToDate(c.getToDate());
        if (c.getSlot() != null) banner.setSlot(c.getSlot());
        if (c.getCtaLabel() != null) banner.setCtaLabel(c.getCtaLabel());
        if (c.getCtaLink() != null) banner.setCtaLink(c.getCtaLink());
        if (c.getSortOrder() != null) banner.setSortOrder(c.getSortOrder());

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
                banner.getPromotionDetails(),
                banner.getSlot() != null ? banner.getSlot() : BannerSlot.HERO,
                banner.getCtaLabel(),
                banner.getCtaLink(),
                banner.getSortOrder() != null ? banner.getSortOrder() : 0
        );
    }
}
