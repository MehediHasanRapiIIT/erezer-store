package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.*;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.PromotionalBanner;
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

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomePageServiceImpl implements HomePageService {

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

        // Get promotional banners
        List<PromotionalBanner> banners = promotionalBannerRepository.findAll();
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

        return response;
    }
}
