package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.OrderItem;
import kn.org.deliverybackend.entity.ProductImage;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.repository.ProductImageRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;

/**
 * Which product picture belongs to an order line: the one for the colour (or
 * other choice) that was ordered. The admin order page and Erezer Invoice both
 * show it, so they always agree.
 */
@Component
@RequiredArgsConstructor
public class OrderLinePicture {

    private final VariantRepository variantRepository;
    private final ProductImageRepository productImageRepository;

    /**
     * The picture of the ordered choice, or null when the line has no such choice
     * or the choice has no picture of its own (then the product's main picture is shown).
     */
    public String ofChoice(OrderItem item) {
        if (item.getVariantId() == null) return null;
        String key = variantRepository.findById(item.getVariantId()).map(Variant::getOptionKey).orElse(null);
        if (key == null || key.isBlank()) return null;
        Collection<String> chosen = ProductOptions.choices(key).values();
        return productImageRepository.findBelongingToAChoice(item.getProductId()).stream()
                .filter(image -> chosen.contains(image.getOptionValueId()))
                .min(Comparator.comparing((ProductImage image) -> image.getSortOrder() == null ? Integer.MAX_VALUE : image.getSortOrder())
                        .thenComparing(ProductImage::getId))
                .map(ProductImage::getUrl)
                .orElse(null);
    }
}
