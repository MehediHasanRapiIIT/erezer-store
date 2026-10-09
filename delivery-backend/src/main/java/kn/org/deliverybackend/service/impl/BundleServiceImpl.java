package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.bundle.BundleOfferRequestDTO;
import kn.org.deliverybackend.dto.bundle.BundleTierDTO;
import kn.org.deliverybackend.enumeration.BundleType;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.dto.bundle.BundleOfferResponseDTO;
import kn.org.deliverybackend.dto.request.order.OrderItemRequestDTO;
import kn.org.deliverybackend.entity.BundleOffer;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.BundleOfferRepository;
import kn.org.deliverybackend.service.BundleService;
import kn.org.deliverybackend.service.ProductService;
import kn.org.deliverybackend.util.SearchText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BundleServiceImpl implements BundleService {

    private final BundleOfferRepository repository;
    private final ProductService productService;

    // ── Public ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<BundleOfferResponseDTO> listActive() {
        return repository.findActive().stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BundleOfferResponseDTO getActive(UUID id) {
        BundleOffer b = repository.findById(id)
                .filter(x -> !Boolean.TRUE.equals(x.getDeleted()) && Boolean.TRUE.equals(x.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Bundle not found: " + id));
        return toDTO(b);
    }

    @Override
    @Transactional(readOnly = true)
    public BundleOfferResponseDTO getFeatured() {
        return repository.findActive().stream()
                .filter(b -> Boolean.TRUE.equals(b.getFeatured()))
                .findFirst()
                .map(this::toDTO)
                .orElse(null);
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<BundleOfferResponseDTO> list(String q, int page, int size) {
        return repository.findForAdmin(SearchText.likePattern(q),
                        PageRequest.of(Math.max(page, 0), SearchText.pageSize(size)))
                .map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public BundleOfferResponseDTO get(UUID id) {
        return toDTO(load(id));
    }

    @Override
    @Transactional
    public BundleOfferResponseDTO create(BundleOfferRequestDTO request) {
        BundleOffer b = new BundleOffer();
        apply(b, request);
        return toDTO(repository.save(b));
    }

    @Override
    @Transactional
    public BundleOfferResponseDTO update(UUID id, BundleOfferRequestDTO request) {
        BundleOffer b = load(id);
        apply(b, request);
        return toDTO(repository.save(b));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        BundleOffer b = load(id);
        b.setDeleted(true);
        b.setIsActive(false);
        repository.save(b);
    }

    // ── Pricing ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public BigDecimal bundleDiscount(UUID bundleId, List<OrderItemRequestDTO> items, BigDecimal subtotal) {
        BundleOffer b = repository.findById(bundleId)
                .filter(x -> !Boolean.TRUE.equals(x.getDeleted()) && Boolean.TRUE.equals(x.getIsActive()))
                .orElseThrow(() -> new InvalidStockOperationException("Bundle offer is not available."));

        int min = BundlePricing.minItems(b);
        int max = BundlePricing.maxItems(b);
        int totalQty = items == null ? 0
                : items.stream().mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum();
        if (min == max && totalQty != min) {
            throw new InvalidStockOperationException(
                    "This bundle needs exactly " + min + " item(s); received " + totalQty + ".");
        }
        if (totalQty < min) {
            throw new InvalidStockOperationException(
                    "This offer starts at " + min + " item(s); received " + totalQty + ".");
        }
        if (totalQty > max) {
            throw new InvalidStockOperationException(
                    "This offer takes up to " + max + " item(s) in one order; received " + totalQty + ".");
        }

        Set<Long> eligible = new HashSet<>(b.getProductIds());
        boolean allEligible = items != null && items.stream()
                .allMatch(i -> i.getProductId() != null && eligible.contains(i.getProductId()));
        if (!allEligible) {
            throw new InvalidStockOperationException("A selected product is not part of this bundle.");
        }

        return BundlePricing.discount(b, totalQty, subtotal);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private BundleOffer load(UUID id) {
        return repository.findById(id)
                .filter(x -> !Boolean.TRUE.equals(x.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Bundle not found: " + id));
    }

    private void apply(BundleOffer b, BundleOfferRequestDTO r) {
        b.setName(r.getName().trim());
        b.setLabel(r.getLabel() == null ? null : r.getLabel().trim());
        b.setDescription(r.getDescription());
        applyOffer(b, r);
        b.setCompareAtPrice(r.getCompareAtPrice());
        b.setIsActive(r.getIsActive() == null ? Boolean.TRUE : r.getIsActive());
        b.setFeatured(r.getFeatured() == null ? Boolean.FALSE : r.getFeatured());
        b.setSortOrder(r.getSortOrder() == null ? 0 : r.getSortOrder());
        b.setImageUrls(r.getImageUrls() == null ? new ArrayList<>() : new ArrayList<>(r.getImageUrls()));
        b.setProductIds(r.getProductIds() == null ? new ArrayList<>()
                : r.getProductIds().stream().filter(java.util.Objects::nonNull).distinct().toList());
    }

    /**
     * The numbers of the offer, by its kind. Each kind needs different ones, so
     * they are checked here rather than on the request, where one rule would
     * have to fit all three.
     */
    private void applyOffer(BundleOffer b, BundleOfferRequestDTO r) {
        BundleType type = BundleType.of(r.getOfferType(), r.getGetCount());
        b.setOfferType(type.name());
        switch (type) {
            case FIXED_PRICE -> {
                if (r.getBuyCount() == null || r.getBuyCount() < 1) {
                    throw new InvalidRequestException("Say how many items are in the bundle.");
                }
                if (r.getBundlePrice() == null || r.getBundlePrice().signum() <= 0) {
                    throw new InvalidRequestException("Give the price of the bundle.");
                }
                b.setBuyCount(r.getBuyCount());
                b.setGetCount(0);
                b.setBundlePrice(r.getBundlePrice());
                b.setTiersJson(null);
            }
            case BUY_X_GET_Y -> {
                if (r.getBuyCount() == null || r.getBuyCount() < 1) {
                    throw new InvalidRequestException("Say how many items the customer pays for.");
                }
                if (r.getGetCount() == null || r.getGetCount() < 1) {
                    throw new InvalidRequestException("Say how many items are free. For none, choose a fixed-price bundle.");
                }
                if (r.getBundlePrice() == null || r.getBundlePrice().signum() <= 0) {
                    throw new InvalidRequestException("Give the price of the bundle.");
                }
                b.setBuyCount(r.getBuyCount());
                b.setGetCount(r.getGetCount());
                b.setBundlePrice(r.getBundlePrice());
                b.setTiersJson(null);
            }
            case QUANTITY_DISCOUNT -> {
                List<BundleTierDTO> tiers = BundlePricing.checkedTiers(r.getTiers());
                b.setTiersJson(BundlePricing.write(tiers));
                // Stored so anything that reads the old fields still sees a sensible offer.
                b.setBuyCount(tiers.get(0).getQuantity());
                b.setGetCount(0);
                b.setBundlePrice(BigDecimal.ZERO);
            }
        }
    }

    private BundleOfferResponseDTO toDTO(BundleOffer b) {
        boolean priced = BundlePricing.typeOf(b) != BundleType.QUANTITY_DISCOUNT;
        // A "was" price only means something beside one fixed price.
        BigDecimal savings = !priced || b.getCompareAtPrice() == null ? null
                : b.getCompareAtPrice().subtract(b.getBundlePrice());
        return BundleOfferResponseDTO.builder()
                .id(b.getId())
                .name(b.getName())
                .label(b.getLabel())
                .description(b.getDescription())
                .offerType(BundlePricing.typeOf(b).name())
                .headline(BundlePricing.headline(b))
                .tiers(BundlePricing.tiersOf(b))
                .minItems(BundlePricing.minItems(b))
                .maxItems(BundlePricing.maxItems(b))
                .buyCount(b.getBuyCount())
                .getCount(b.getGetCount())
                .slots(BundlePricing.minItems(b))
                .bundlePrice(b.getBundlePrice())
                .compareAtPrice(priced ? b.getCompareAtPrice() : null)
                .savings(savings)
                .isActive(b.getIsActive())
                .featured(b.getFeatured())
                .sortOrder(b.getSortOrder())
                .images(new ArrayList<>(b.getImageUrls()))
                .products(productService.getProductsByIds(b.getProductIds()))
                .build();
    }
}
