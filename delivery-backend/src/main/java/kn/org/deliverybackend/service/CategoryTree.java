package kn.org.deliverybackend.service;

import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.repository.CategoryRepository;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Categories and their subcategories.
 *
 * <p>A subcategory is a category with a parent, and there are two levels only:
 * a subcategory can't have subcategories of its own. Two rules follow, and
 * every feature that works "by category" goes through here so they hold
 * everywhere:
 *
 * <ul>
 *   <li>A main category covers what is in it and in its subcategories:
 *       opening Hoodies shows the Zip Hoodies too.</li>
 *   <li>A subcategory follows its parent — delivery charge, "never discount",
 *       showing stock as a number — unless it sets its own.</li>
 * </ul>
 */
public final class CategoryTree {

    private CategoryTree() {}

    /** The category itself and, for a main category, its subcategories. Empty for null. */
    public static Set<Long> family(CategoryRepository categories, Long categoryId) {
        Set<Long> ids = new LinkedHashSet<>();
        if (categoryId == null) return ids;
        ids.add(categoryId);
        for (Category child : categories.findByParentIdAndDeletedFalse(categoryId)) {
            ids.add(child.getId());
        }
        return ids;
    }

    /** The main category a subcategory sits under; empty for a main category. */
    public static Optional<Category> parentOf(CategoryRepository categories, Category category) {
        if (category == null || category.getParentId() == null) return Optional.empty();
        return categories.findById(category.getParentId()).filter(p -> !Boolean.TRUE.equals(p.getDeleted()));
    }

    /** Its own delivery charge, else its parent's. Null means the area's price decides. */
    public static BigDecimal shippingCharge(CategoryRepository categories, Category category) {
        if (category == null) return null;
        if (category.getShippingCharge() != null) return category.getShippingCharge();
        return parentOf(categories, category).map(Category::getShippingCharge).orElse(null);
    }

    /** Kept at full price because it, or the main category it sits under, is. */
    public static boolean discountExcluded(CategoryRepository categories, Category category) {
        if (category == null) return false;
        if (Boolean.TRUE.equals(category.getDiscountExcluded())) return true;
        return parentOf(categories, category).map(p -> Boolean.TRUE.equals(p.getDiscountExcluded())).orElse(false);
    }

    /** Product pages show the stock as a number because it, or its parent, says so. */
    public static Boolean showStockQuantity(CategoryRepository categories, Category category) {
        if (category == null) return null;
        if (Boolean.TRUE.equals(category.getShowStockQuantity())) return true;
        return parentOf(categories, category).map(Category::getShowStockQuantity)
                .orElse(category.getShowStockQuantity());
    }

    /** "Hoodies › Zip Hoodies" for a subcategory, just the name for a main category. */
    public static String path(CategoryRepository categories, Category category) {
        if (category == null) return null;
        return parentOf(categories, category).map(p -> p.getName() + " › " + category.getName())
                .orElse(category.getName());
    }
}
