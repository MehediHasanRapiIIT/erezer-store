package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.checkout.CheckoutQuoteRequestDTO;
import kn.org.deliverybackend.dto.checkout.CheckoutQuoteResponseDTO;
import kn.org.deliverybackend.dto.coupon.CouponValidateRequestDTO;
import kn.org.deliverybackend.dto.coupon.CouponValidateResponseDTO;
import kn.org.deliverybackend.dto.request.order.OrderItemRequestDTO;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.ShippingZone;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.ShippingZoneRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import kn.org.deliverybackend.service.BundleService;
import kn.org.deliverybackend.service.CheckoutQuoteService;
import kn.org.deliverybackend.service.CouponService;
import kn.org.deliverybackend.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CheckoutQuoteServiceImpl implements CheckoutQuoteService {

    private final ProductRepository productRepository;
    private final VariantRepository variantRepository;
    private final ShippingZoneRepository shippingZoneRepository;
    private final ShippingService shippingService;
    private final CouponService couponService;
    private final DiscountEngine discountEngine;
    private final BundleService bundleService;

    @Override
    @Transactional(readOnly = true)
    public CheckoutQuoteResponseDTO quote(CheckoutQuoteRequestDTO request) {
        // 1. Sum current product prices × quantities to get the subtotal, and
        //    accumulate any automatic (product/category/global) discounts per line.
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal autoDiscount = BigDecimal.ZERO;
        BigDecimal customSurcharge = BigDecimal.ZERO;
        for (OrderItemRequestDTO item : request.getItems()) {
            Product p = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found: " + item.getProductId()));
            Variant variant = item.getVariantId() != null
                    ? variantRepository.findById(item.getVariantId())
                            .filter(v -> !Boolean.TRUE.equals(v.getDeleted()))
                            .orElse(null)
                    : null;
            BigDecimal unit = PricingSupport.effectiveUnitPrice(p, variant);
            BigDecimal lineSubtotal = unit.multiply(BigDecimal.valueOf(item.getQuantity()));
            subtotal = subtotal.add(lineSubtotal);
            autoDiscount = autoDiscount.add(
                    discountEngine.discountForLine(p.getId(), p.getCategoryId(), lineSubtotal));
            // Flat custom-size surcharge (server-authoritative) for made-to-order lines.
            if (item.getCustomMeasurements() != null && !item.getCustomMeasurements().isBlank()
                    && Boolean.TRUE.equals(p.getCustomSizeEnabled()) && p.getCustomSizeSurcharge() != null) {
                customSurcharge = customSurcharge.add(p.getCustomSizeSurcharge());
            }
        }

        // 2. Resolve the shipping zone.
        ShippingZone zone = request.getShippingZoneId() != null
                ? shippingZoneRepository.findById(request.getShippingZoneId())
                        .filter(z -> !Boolean.TRUE.equals(z.getDeleted())
                                && Boolean.TRUE.equals(z.getIsActive()))
                        .orElse(shippingService.defaultZone())
                : shippingService.resolveZone(request.getDeliveryAddress());

        BigDecimal shippingFee = shippingService.computeFee(zone, subtotal);

        // 3. Discounts. A bundle offer is its own fixed deal and does NOT stack
        //    with coupons or automatic discounts; otherwise apply those as before.
        BigDecimal discountAmount;
        String couponDiscountType = null;
        String couponMessage = null;
        boolean couponApplied = false;
        String couponCode = request.getCouponCode();

        if (request.getBundleId() != null) {
            discountAmount = bundleService.bundleDiscount(request.getBundleId(), request.getItems(), subtotal);
            couponCode = null;
            // Free-shipping thresholds must be judged on the price the customer
            // actually pays for goods (the bundle price), not the inflated
            // pre-discount list subtotal — otherwise a bundle wrongly earns free
            // shipping even when its bundle price is below the free-above cutoff.
            BigDecimal bundleGoods = subtotal.subtract(discountAmount);
            if (bundleGoods.signum() < 0) bundleGoods = BigDecimal.ZERO;
            shippingFee = shippingService.computeFee(zone, bundleGoods);
        } else {
            // Coupon applies on the subtotal AFTER automatic discounts.
            BigDecimal couponBase = subtotal.subtract(autoDiscount);
            if (couponBase.signum() < 0) couponBase = BigDecimal.ZERO;
            discountAmount = autoDiscount;
            if (couponCode != null && !couponCode.isBlank()) {
                CouponValidateResponseDTO validation = couponService.validate(
                        new CouponValidateRequestDTO(couponCode, couponBase, request.getUserId()));
                if (validation.isValid()) {
                    couponApplied = true;
                    couponDiscountType = validation.getDiscountType();
                    discountAmount = discountAmount.add(validation.getDiscountAmount() == null
                            ? BigDecimal.ZERO : validation.getDiscountAmount());
                    if (validation.isRemovesShipping()) {
                        shippingFee = BigDecimal.ZERO;
                    }
                } else {
                    couponMessage = validation.getReason();
                }
            }
        }

        // 4. Tax applies on (subtotal - total discount) — shipping is usually excluded.
        BigDecimal taxable = subtotal.subtract(discountAmount);
        if (taxable.signum() < 0) taxable = BigDecimal.ZERO;
        BigDecimal taxAmount = shippingService.computeTax(zone != null ? zone.getId() : null, taxable);

        BigDecimal total = subtotal
                .subtract(discountAmount)
                .add(shippingFee)
                .add(taxAmount)
                .add(customSurcharge);
        if (total.signum() < 0) total = BigDecimal.ZERO;

        return CheckoutQuoteResponseDTO.builder()
                .subtotal(subtotal)
                .shippingFee(shippingFee)
                .taxAmount(taxAmount)
                .discountAmount(discountAmount)
                .customSurcharge(customSurcharge)
                .total(total)
                .shippingZoneId(zone != null ? zone.getId() : null)
                .shippingZoneName(zone != null ? zone.getDisplayName() : null)
                .couponCode(couponCode)
                .couponDiscountType(couponDiscountType)
                .couponMessage(couponMessage)
                .couponApplied(couponApplied)
                .build();
    }
}
