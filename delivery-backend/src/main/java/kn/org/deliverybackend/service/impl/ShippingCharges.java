package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.shipping.BasketShipping;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads the delivery charges an admin set on products and categories.
 *
 * <p>For one product: its own charge wins; failing that its category's; failing
 * that nothing, and the customer's area price decides. A charge of zero is a
 * real answer — it means delivered free — so "no charge set" has to stay null
 * all the way through rather than becoming zero anywhere.
 *
 * <p>Used by checkout and by the saved order, so a customer is charged what they
 * were quoted.
 */
@Service
@RequiredArgsConstructor
public class ShippingCharges {

    private final CategoryRepository categoryRepository;

    /** The charge set for this product, or null when the area's price should decide. */
    @Transactional(readOnly = true)
    public BigDecimal forProduct(Product product) {
        if (product == null) {
            return null;
        }
        if (product.getShippingCharge() != null) {
            return product.getShippingCharge();
        }
        return forCategory(product.getCategoryId(), new HashMap<>());
    }

    /**
     * What a whole basket says about delivery. Products are passed in because
     * both callers have already loaded them to price the goods.
     */
    @Transactional(readOnly = true)
    public BasketShipping forBasket(Collection<Product> products) {
        if (products == null || products.isEmpty()) {
            return BasketShipping.areaPriceOnly();
        }
        // One lookup per category however many lines share it.
        Map<Long, BigDecimal> categoryCharges = new HashMap<>();
        BigDecimal highest = null;
        boolean usesAreaPrice = false;

        for (Product product : products) {
            BigDecimal charge = product != null && product.getShippingCharge() != null
                    ? product.getShippingCharge()
                    : forCategory(product == null ? null : product.getCategoryId(), categoryCharges);
            if (charge == null) {
                usesAreaPrice = true;
            } else {
                highest = highest == null ? charge : highest.max(charge);
            }
        }
        return new BasketShipping(highest, usesAreaPrice);
    }

    /** A category's charge, remembered per call so a basket of ten shirts asks once. */
    private BigDecimal forCategory(Long categoryId, Map<Long, BigDecimal> seen) {
        if (categoryId == null) {
            return null;
        }
        if (seen.containsKey(categoryId)) {
            return seen.get(categoryId);
        }
        BigDecimal charge = categoryRepository.findById(categoryId)
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .map(Category::getShippingCharge)
                .orElse(null);
        seen.put(categoryId, charge);
        return charge;
    }
}
