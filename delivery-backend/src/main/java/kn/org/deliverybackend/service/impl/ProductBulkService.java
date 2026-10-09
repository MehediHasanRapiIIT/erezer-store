package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.product.BulkProductActionDTO;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.enumeration.BulkProductAction;
import kn.org.deliverybackend.enumeration.StockDisplay;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One action for many products at once, from the ticks on the Products page:
 * move them to a category, delete them, or flip one of their switches.
 *
 * <p>Each product is changed exactly as the same action on its own would change
 * it, and the whole lot is one transaction: if any of it fails, none of it
 * happens. Moving a product leaves its code alone - a code is printed on
 * labels and quoted in orders, so it does not follow the category.
 */
@Service
@RequiredArgsConstructor
public class ProductBulkService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductService productService;
    private final SizeChartLibraryService sizeCharts;

    /** What a bulk action did, for the message the admin sees. */
    public record Result(int changed, String message) {
    }

    @Transactional
    public Result apply(BulkProductActionDTO request) {
        BulkProductAction action = request.getAction();
        if (action == null) throw new InvalidRequestException("Choose what to do.");
        Set<Long> ids = new LinkedHashSet<>(request.getProductIds() == null ? List.of() : request.getProductIds());
        ids.remove(null);
        if (ids.isEmpty()) throw new InvalidRequestException("Tick at least one product.");

        List<Product> products = productRepository.findAllById(ids).stream()
                .filter(p -> !Boolean.TRUE.equals(p.getDeleted())).toList();
        // Someone else deleted one in the meantime: say so rather than quietly do fewer.
        if (products.size() != ids.size()) {
            int gone = ids.size() - products.size();
            throw new InvalidRequestException(gone + (gone == 1 ? " of the ticked products no longer exists."
                    : " of the ticked products no longer exist.") + " Refresh the list and try again.");
        }
        int n = products.size();
        String these = n + (n == 1 ? " product" : " products");

        return switch (action) {
            case MOVE_CATEGORY -> move(products, request.getCategoryId());
            case DELETE -> {
                for (Product p : products) productService.deleteProduct(p.getId());
                yield new Result(n, these + " deleted.");
            }
            case SHOW -> each(products, p -> p.setIsAvailable(true), these + (n == 1 ? " is" : " are") + " now on sale in the shop.");
            case HIDE -> each(products, p -> p.setIsAvailable(false), these + " hidden from the shop.");
            case FEATURE -> each(products, p -> p.setIsFeatured(true), these + " featured on the home page.");
            case UNFEATURE -> each(products, p -> p.setIsFeatured(false), these + " no longer featured.");
            case NEW_ARRIVAL_ON -> each(products, p -> p.setIsNewArrival(true), these + " marked as new arrivals.");
            case NEW_ARRIVAL_OFF -> each(products, p -> p.setIsNewArrival(false), these + " no longer marked as new arrivals.");
            case NEVER_DISCOUNT_ON -> each(products, p -> p.setDiscountExcluded(true), these + " will never be discounted automatically.");
            case NEVER_DISCOUNT_OFF -> each(products, p -> p.setDiscountExcluded(false), these + " can be discounted again.");
            case STOCK_SHOW_QUANTITY -> each(products, p -> p.setStockDisplay(StockDisplay.QUANTITY), these + " now show the stock quantity.");
            case STOCK_SHOW_LABELS -> each(products, p -> p.setStockDisplay(StockDisplay.LABEL), these + " now show stock labels.");
            case SET_SIZE_CHART -> {
                Long chartId = sizeCharts.checked(request.getSizeChartId());
                yield each(products, p -> p.setSizeChartId(chartId), chartId == null
                        ? these + (n == 1 ? " now follows its" : " now follow their") + " category's size chart."
                        : these + (n == 1 ? " now uses" : " now use") + " the chosen size chart.");
            }
        };
    }

    private Result move(List<Product> products, Long categoryId) {
        if (categoryId == null) throw new InvalidRequestException("Choose the category to move them to.");
        Category category = categoryRepository.findById(categoryId)
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
        int moved = 0;
        for (Product p : products) {
            if (categoryId.equals(p.getCategoryId())) continue;
            p.setCategoryId(categoryId);
            moved++;
        }
        productRepository.saveAll(products);
        int stayed = products.size() - moved;
        return new Result(moved, moved + (moved == 1 ? " product" : " products") + " moved to " + category.getName() + "."
                + (stayed > 0 ? " " + stayed + (stayed == 1 ? " was" : " were") + " already there." : ""));
    }

    private Result each(List<Product> products, Consumer<Product> change, String message) {
        products.forEach(change);
        productRepository.saveAll(products);
        return new Result(products.size(), message);
    }
}
