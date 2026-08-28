package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.bundle.BundleOfferRequestDTO;
import kn.org.deliverybackend.dto.bundle.BundleOfferResponseDTO;
import kn.org.deliverybackend.dto.request.order.OrderItemRequestDTO;
import kn.org.deliverybackend.entity.BundleOffer;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.BundleOfferRepository;
import kn.org.deliverybackend.service.BundleService;
import kn.org.deliverybackend.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public List<BundleOfferResponseDTO> listAll() {
        return repository.findAllForAdmin().stream().map(this::toDTO).toList();
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

        int slots = b.slots();
        int totalQty = items == null ? 0
                : items.stream().mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum();
        if (totalQty != slots) {
            throw new InvalidStockOperationException(
                    "This bundle needs exactly " + slots + " item(s); received " + totalQty + ".");
        }

        Set<Long> eligible = new HashSet<>(b.getProductIds());
        boolean allEligible = items != null && items.stream()
                .allMatch(i -> i.getProductId() != null && eligible.contains(i.getProductId()));
        if (!allEligible) {
            throw new InvalidStockOperationException("A selected product is not part of this bundle.");
        }

        BigDecimal discount = (subtotal == null ? BigDecimal.ZERO : subtotal).subtract(b.getBundlePrice());
        return discount.signum() < 0 ? BigDecimal.ZERO : discount;
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
        b.setBuyCount(r.getBuyCount());
        b.setGetCount(r.getGetCount());
        b.setBundlePrice(r.getBundlePrice());
        b.setCompareAtPrice(r.getCompareAtPrice());
        b.setIsActive(r.getIsActive() == null ? Boolean.TRUE : r.getIsActive());
        b.setFeatured(r.getFeatured() == null ? Boolean.FALSE : r.getFeatured());
        b.setSortOrder(r.getSortOrder() == null ? 0 : r.getSortOrder());
        b.setImageUrls(r.getImageUrls() == null ? new ArrayList<>() : new ArrayList<>(r.getImageUrls()));
        b.setProductIds(r.getProductIds() == null ? new ArrayList<>()
                : r.getProductIds().stream().filter(java.util.Objects::nonNull).distinct().toList());
    }

    private BundleOfferResponseDTO toDTO(BundleOffer b) {
        BigDecimal savings = b.getCompareAtPrice() == null ? null
                : b.getCompareAtPrice().subtract(b.getBundlePrice());
        return BundleOfferResponseDTO.builder()
                .id(b.getId())
                .name(b.getName())
                .label(b.getLabel())
                .description(b.getDescription())
                .buyCount(b.getBuyCount())
                .getCount(b.getGetCount())
                .slots(b.slots())
                .bundlePrice(b.getBundlePrice())
                .compareAtPrice(b.getCompareAtPrice())
                .savings(savings)
                .isActive(b.getIsActive())
                .featured(b.getFeatured())
                .sortOrder(b.getSortOrder())
                .images(new ArrayList<>(b.getImageUrls()))
                .products(productService.getProductsByIds(b.getProductIds()))
                .build();
    }
}
