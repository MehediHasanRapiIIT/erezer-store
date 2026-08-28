package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.*;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.dto.HomeCategorySectionDTO;
import kn.org.deliverybackend.entity.PromotionalBanner;
import kn.org.deliverybackend.enumeration.BannerSlot;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.PromotionalBannerRepository;
import kn.org.deliverybackend.service.HomePageService;
import kn.org.deliverybackend.mapper.CategoryMapper;
import kn.org.deliverybackend.mapper.ProductMapper;
import kn.org.deliverybackend.mapper.PromotionalBannerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomePageServiceImpl implements HomePageService {

    /** Products shown in each promoted category band on the landing page. */
    private static final int HOME_SECTION_PRODUCT_LIMIT = 5;

    private final PromotionalBannerRepository promotionalBannerRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final PromotionalBannerMapper promotionalBannerMapper;
    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional(readOnly = true)
    public HomePageResponseDTO getHomePageData() {
        HomePageResponseDTO response = new HomePageResponseDTO();

        // Promotional banners, ready for the storefront to group by slot.
        //
        // findAll() previously shipped every row, including soft-deleted ones
        // and campaigns whose window had closed, so a retired banner kept
        // showing. Filter to what is genuinely live and order within each slot.
        LocalDate today = LocalDate.now();
        List<PromotionalBanner> banners = promotionalBannerRepository.findAll().stream()
                .filter(b -> !Boolean.TRUE.equals(b.getDeleted()))
                .filter(b -> b.getFromDate() == null || !b.getFromDate().isAfter(today))
                .filter(b -> b.getToDate() == null || !b.getToDate().isBefore(today))
                .sorted(Comparator
                        .comparing((PromotionalBanner b) ->
                                b.getSlot() != null ? b.getSlot().name() : BannerSlot.HERO.name())
                        .thenComparing(b -> b.getSortOrder() != null ? b.getSortOrder() : 0))
                .collect(Collectors.toList());
        response.setBanners(banners.stream()
                .map(promotionalBannerMapper::toDTO)
                .collect(Collectors.toList()));

        // Get categories (active only)
        List<Category> categories = categoryRepository.findAll().stream()
                .filter(Category::getIsActive)
                .collect(Collectors.toList());
        response.setCategories(categoryMapper.toDTOs(categories));

        // Get popular items (limit to 10)
        List<Product> popularItems = productRepository.findTop10ByOrderByCreatedAtDesc();
        response.setPopularItems(popularItems.stream()
                .map(productMapper::toResponseDTO)
                .collect(Collectors.toList()));

        // Featured: admin-flagged products (limit 9). Fall back to newest 9 so the
        // section isn't empty before any product has been flagged.
        List<Product> featuredItems = productRepository.findTop9ByIsFeaturedTrueOrderByCreatedAtDesc();
        if (featuredItems.isEmpty()) {
            featuredItems = productRepository.findTop9ByOrderByCreatedAtDesc();
        }
        response.setFeaturedItems(featuredItems.stream()
                .map(productMapper::toResponseDTO)
                .collect(Collectors.toList()));

        // New arrivals: admin-flagged products (limit 9). Fall back to newest 9
        // so the section isn't empty before any product has been flagged.
        List<Product> newArrivals = productRepository.findTop9ByIsNewArrivalTrueOrderByCreatedAtDesc();
        if (newArrivals.isEmpty()) {
            newArrivals = productRepository.findTop9ByOrderByCreatedAtDesc();
        }
        response.setNewArrivalItems(newArrivals.stream()
                .map(productMapper::toResponseDTO)
                .collect(Collectors.toList()));

        // Admin-promoted category bands, e.g. "Erezer Pink". Products are bundled
        // in here so the landing page still loads in one request rather than one
        // per section.
        response.setHomeSections(categoryRepository
                .findByShowOnHomeTrueAndIsActiveTrueAndDeletedFalseOrderByHomeSortOrderAscNameAsc()
                .stream()
                .map(this::toHomeSection)
                // A promoted category with nothing in it would render an empty
                // band, so drop it rather than showing a bare heading.
                .filter(section -> !section.getProducts().isEmpty())
                .collect(Collectors.toList()));

        return response;
    }

    private HomeCategorySectionDTO toHomeSection(Category category) {
        List<ProductResponseDTO> products = productRepository.findByCategoryId(category.getId()).stream()
                .filter(pr -> !Boolean.TRUE.equals(pr.getDeleted()))
                .filter(pr -> !Boolean.FALSE.equals(pr.getIsAvailable()))
                .limit(HOME_SECTION_PRODUCT_LIMIT)
                .map(productMapper::toResponseDTO)
                .collect(Collectors.toList());

        return HomeCategorySectionDTO.builder()
                .categoryId(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .imageUrl(category.getImageUrl())
                .sortOrder(category.getHomeSortOrder() != null ? category.getHomeSortOrder() : 0)
                .products(products)
                .build();
    }
}
