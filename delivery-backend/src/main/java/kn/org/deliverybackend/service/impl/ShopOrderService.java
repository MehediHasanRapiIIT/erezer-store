package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.response.product.ShopOrderItemDTO;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The order products are shown in on the Shop page and the category pages.
 *
 * <p>The admin ranks the products that matter: first, second, third. The shop
 * shows those first, in that order, and every other product after them as it
 * always did. A ranked product that has run out of stock gives up its place
 * until it is back (see {@code ProductServiceImpl.browseOrder}); its rank is
 * kept, so it returns to the same place by itself.
 *
 * <p>The whole order is saved at once, as the list of product ids from first to
 * last. Moving, adding and removing are all just a new list.
 */
@Service
@RequiredArgsConstructor
public class ShopOrderService {

    /** More than anyone arranges by hand; keeps one save a bounded piece of work. */
    public static final int MAX_RANKED = 500;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    /** The ranked products, first to last. */
    @Transactional(readOnly = true)
    public List<ShopOrderItemDTO> list() {
        List<Product> ranked = productRepository.findRanked();
        Map<Long, String> categoryNames = new HashMap<>();
        Set<Long> categoryIds = new LinkedHashSet<>();
        for (Product p : ranked) {
            if (p.getCategoryId() != null) categoryIds.add(p.getCategoryId());
        }
        for (Category c : categoryRepository.findAllById(categoryIds)) categoryNames.put(c.getId(), c.getName());

        List<ShopOrderItemDTO> items = new ArrayList<>();
        for (Product p : ranked) {
            // Positions are counted here, so a deleted product never leaves a gap in what the admin sees.
            items.add(new ShopOrderItemDTO(p.getId(), items.size() + 1, p.getName(), p.getProductCode(), p.getSku(),
                    p.getImageUrl(), categoryNames.get(p.getCategoryId()), p.getPrice(), p.getStockQuantity(),
                    !Boolean.FALSE.equals(p.getIsAvailable())));
        }
        return items;
    }

    /**
     * Makes {@code productIds} the shop's order, first to last. A product not in
     * the list is no longer ranked. An id given twice counts once, where it first
     * appears; an id that is not a product (or is a deleted one) is refused.
     */
    @Transactional
    public List<ShopOrderItemDTO> set(List<Long> productIds) {
        List<Long> order = new ArrayList<>(new LinkedHashSet<>(productIds == null ? List.<Long>of() : productIds));
        order.remove(null);
        if (order.size() > MAX_RANKED) {
            throw new InvalidRequestException("At most " + MAX_RANKED + " products can be ranked. The rest follow them in the shop by themselves.");
        }
        if (!order.isEmpty()) {
            Set<Long> real = new LinkedHashSet<>(productRepository.findLiveIds(order));
            List<Long> unknown = order.stream().filter(id -> !real.contains(id)).toList();
            if (!unknown.isEmpty()) {
                throw new InvalidRequestException("A product in the list no longer exists (id " + unknown.get(0) + "). Reload the page and try again.");
            }
        }
        productRepository.clearShopRanks();
        for (int i = 0; i < order.size(); i++) {
            productRepository.setShopRank(order.get(i), i + 1);
        }
        return list();
    }
}
