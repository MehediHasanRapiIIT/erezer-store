package kn.org.deliverybackend.service;

import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.repository.CategoryRepository;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Categories and their subcategories, to any depth.
 *
 * <p>A subcategory is a category with a parent, and it can have subcategories
 * of its own: Men › T-Shirts › Drop Shoulder. Two rules follow, and every
 * feature that works "by category" goes through here so they hold everywhere:
 *
 * <ul>
 *   <li>A category covers what is in it and in everything under it: opening
 *       Men shows the Drop Shoulder T-Shirts too.</li>
 *   <li>A subcategory follows the categories above it — delivery charge,
 *       "never discount", showing stock as a number — taking each from the
 *       nearest one that sets it, unless it sets its own.</li>
 * </ul>
 *
 * <p>Saving refuses a loop (a category under its own subcategory), but every
 * walk here still stops at a category it has already seen, so bad data can
 * never hang a request.
 */
public final class CategoryTree {

    private CategoryTree() {}

    /** The category itself and everything under it, at every level. Empty for null. */
    public static Set<Long> family(CategoryRepository categories, Long categoryId) {
        Set<Long> ids = new LinkedHashSet<>();
        if (categoryId == null) return ids;
        Deque<Long> toVisit = new ArrayDeque<>();
        toVisit.add(categoryId);
        while (!toVisit.isEmpty()) {
            Long id = toVisit.poll();
            if (!ids.add(id)) continue;
            for (Category child : categories.findByParentIdAndDeletedFalse(id)) {
                if (child.getId() != null && !ids.contains(child.getId())) toVisit.add(child.getId());
            }
        }
        return ids;
    }

    /** The category directly above; empty for a main category. */
    public static Optional<Category> parentOf(CategoryRepository categories, Category category) {
        if (category == null || category.getParentId() == null) return Optional.empty();
        return categories.findById(category.getParentId()).filter(p -> !Boolean.TRUE.equals(p.getDeleted()));
    }

    /** The categories above this one, nearest first, ending at its main category. Empty for a main category. */
    public static List<Category> ancestors(CategoryRepository categories, Category category) {
        List<Category> above = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        if (category != null && category.getId() != null) seen.add(category.getId());
        Optional<Category> parent = parentOf(categories, category);
        while (parent.isPresent() && (parent.get().getId() == null || seen.add(parent.get().getId()))) {
            above.add(parent.get());
            parent = parentOf(categories, parent.get());
        }
        return above;
    }

    /** How far down it sits: 0 for a main category, 1 for its subcategory, and so on. */
    public static int depth(CategoryRepository categories, Category category) {
        return ancestors(categories, category).size();
    }

    /** Its own delivery charge, else that of the nearest category above that has one. Null means the area's price decides. */
    public static BigDecimal shippingCharge(CategoryRepository categories, Category category) {
        if (category == null) return null;
        if (category.getShippingCharge() != null) return category.getShippingCharge();
        for (Category above : ancestors(categories, category)) {
            if (above.getShippingCharge() != null) return above.getShippingCharge();
        }
        return null;
    }

    /** Kept at full price because it, or any category it sits under, is. */
    public static boolean discountExcluded(CategoryRepository categories, Category category) {
        if (category == null) return false;
        if (Boolean.TRUE.equals(category.getDiscountExcluded())) return true;
        for (Category above : ancestors(categories, category)) {
            if (Boolean.TRUE.equals(above.getDiscountExcluded())) return true;
        }
        return false;
    }

    /** Product pages show the stock as a number because it, or a category it sits under, says so. */
    public static Boolean showStockQuantity(CategoryRepository categories, Category category) {
        if (category == null) return null;
        if (Boolean.TRUE.equals(category.getShowStockQuantity())) return true;
        List<Category> above = ancestors(categories, category);
        for (Category a : above) {
            if (Boolean.TRUE.equals(a.getShowStockQuantity())) return true;
        }
        return above.isEmpty() ? category.getShowStockQuantity() : above.get(0).getShowStockQuantity();
    }

    /** "Men › T-Shirts › Drop Shoulder" for a subcategory, just the name for a main category. */
    public static String path(CategoryRepository categories, Category category) {
        if (category == null) return null;
        List<Category> chain = new ArrayList<>(ancestors(categories, category));
        Collections.reverse(chain);
        StringBuilder path = new StringBuilder();
        for (Category above : chain) path.append(above.getName()).append(" › ");
        return path.append(category.getName()).toString();
    }
}
