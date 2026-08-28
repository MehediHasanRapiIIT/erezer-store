package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.response.search.AdminSearchResponse;
import kn.org.deliverybackend.dto.response.search.SearchGroup;
import kn.org.deliverybackend.dto.response.search.SearchItem;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Review;
import kn.org.deliverybackend.entity.UserRider;
import kn.org.deliverybackend.entity.Users;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.ReviewRepository;
import kn.org.deliverybackend.repository.UserRiderRepository;
import kn.org.deliverybackend.repository.UsersRepository;
import kn.org.deliverybackend.service.AdminSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminSearchServiceImpl implements AdminSearchService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final UsersRepository usersRepository;
    private final UserRiderRepository userRiderRepository;
    private final ReviewRepository reviewRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminSearchResponse search(String query, int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return AdminSearchResponse.builder()
                    .query(q)
                    .products(SearchGroup.empty())
                    .categories(SearchGroup.empty())
                    .orders(SearchGroup.empty())
                    .customers(SearchGroup.empty())
                    .riders(SearchGroup.empty())
                    .reviews(SearchGroup.empty())
                    .totalCount(0L)
                    .build();
        }

        int safeLimit = Math.min(Math.max(limit, 1), 50);
        Pageable pageable = PageRequest.of(0, safeLimit);

        SearchGroup products = toProducts(productRepository.searchAdmin(q, pageable));
        SearchGroup categories = toCategories(categoryRepository.searchAdmin(q, pageable));
        SearchGroup orders = toOrders(orderRepository.searchAdmin(q, pageable));
        SearchGroup customers = toCustomers(usersRepository.searchAdmin(q, pageable));
        SearchGroup riders = toRiders(userRiderRepository.searchAdmin(q, pageable));
        SearchGroup reviews = toReviews(reviewRepository.searchAdmin(q, pageable));

        long total = products.getTotal() + categories.getTotal() + orders.getTotal()
                + customers.getTotal() + riders.getTotal() + reviews.getTotal();

        return AdminSearchResponse.builder()
                .query(q)
                .products(products)
                .categories(categories)
                .orders(orders)
                .customers(customers)
                .riders(riders)
                .reviews(reviews)
                .totalCount(total)
                .build();
    }

    private SearchGroup toProducts(Page<Product> page) {
        Map<Long, String> categoryNames = lookupCategoryNames(page.getContent());
        List<SearchItem> items = page.getContent().stream().map(p -> {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("price", p.getPrice());
            extra.put("stockQuantity", p.getStockQuantity());
            extra.put("isAvailable", p.getIsAvailable());
            if (p.getSku() != null) extra.put("sku", p.getSku());
            if (p.getCategoryId() != null) {
                extra.put("categoryId", p.getCategoryId());
                String catName = categoryNames.get(p.getCategoryId());
                if (catName != null) extra.put("categoryName", catName);
            }
            return SearchItem.builder()
                    .id(String.valueOf(p.getId()))
                    .type("PRODUCT")
                    .title(p.getName())
                    .subtitle(p.getSku() != null ? p.getSku() : p.getDescription())
                    .imageUrl(p.getImageUrl())
                    .extra(extra)
                    .build();
        }).toList();
        return SearchGroup.builder().items(items).total(page.getTotalElements()).build();
    }

    private Map<Long, String> lookupCategoryNames(List<Product> products) {
        List<Long> ids = products.stream()
                .map(Product::getCategoryId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, String> result = new HashMap<>();
        for (Category c : categoryRepository.findAllById(ids)) {
            result.put(c.getId(), c.getName());
        }
        return result;
    }

    private SearchGroup toCategories(Page<Category> page) {
        List<SearchItem> items = page.getContent().stream().map(c -> {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("isActive", c.getIsActive());
            return SearchItem.builder()
                    .id(String.valueOf(c.getId()))
                    .type("CATEGORY")
                    .title(c.getName())
                    .extra(extra)
                    .build();
        }).toList();
        return SearchGroup.builder().items(items).total(page.getTotalElements()).build();
    }

    private SearchGroup toOrders(Page<Order> page) {
        List<SearchItem> items = page.getContent().stream().map(o -> {
            String idStr = o.getId() != null ? o.getId().toString() : "";
            String shortId = idStr.length() >= 8 ? "#" + idStr.substring(0, 8) : "#" + idStr;
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("orderStatus", o.getOrderStatus());
            extra.put("totalAmount", o.getTotalAmount());
            extra.put("paymentMethod", o.getPaymentMethod());
            return SearchItem.builder()
                    .id(idStr)
                    .type("ORDER")
                    .title(shortId)
                    .subtitle(o.getOrderStatus())
                    .extra(extra)
                    .build();
        }).toList();
        return SearchGroup.builder().items(items).total(page.getTotalElements()).build();
    }

    private SearchGroup toCustomers(Page<Users> page) {
        List<SearchItem> items = page.getContent().stream().map(u -> {
            String name = joinName(u.getFirstName(), u.getLastName());
            if (name.isEmpty()) name = u.getPhoneNumber() != null ? u.getPhoneNumber() : "(unnamed)";
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("phoneNumber", u.getPhoneNumber());
            extra.put("email", u.getEmail());
            extra.put("isActive", u.getIsActive());
            return SearchItem.builder()
                    .id(u.getId().toString())
                    .type("CUSTOMER")
                    .title(name)
                    .subtitle(u.getEmail() != null ? u.getEmail() : u.getPhoneNumber())
                    .imageUrl(u.getProfileImage())
                    .extra(extra)
                    .build();
        }).toList();
        return SearchGroup.builder().items(items).total(page.getTotalElements()).build();
    }

    private SearchGroup toRiders(Page<UserRider> page) {
        List<SearchItem> items = page.getContent().stream().map(r -> {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("status", r.getStatus());
            extra.put("rating", r.getRating());
            extra.put("vehicleType", r.getVehicleType());
            extra.put("plateNumber", r.getPlateNumber());
            return SearchItem.builder()
                    .id(r.getId().toString())
                    .type("RIDER")
                    .title(r.getName() != null ? r.getName() : "(unnamed rider)")
                    .subtitle(r.getContactPhone() != null ? r.getContactPhone() : r.getContactNo())
                    .imageUrl(r.getImageUrl())
                    .extra(extra)
                    .build();
        }).toList();
        return SearchGroup.builder().items(items).total(page.getTotalElements()).build();
    }

    private SearchGroup toReviews(Page<Review> page) {
        List<SearchItem> items = page.getContent().stream().map(r -> {
            String comment = r.getComment() != null ? r.getComment() : "";
            String title = comment.length() > 60 ? comment.substring(0, 60) + "…" : comment;
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("rating", r.getRating());
            extra.put("productId", r.getProductId());
            extra.put("userId", r.getUserId() != null ? r.getUserId().toString() : null);
            extra.put("orderId", r.getOrderId() != null ? r.getOrderId().toString() : null);
            return SearchItem.builder()
                    .id(r.getId().toString())
                    .type("REVIEW")
                    .title(title.isEmpty() ? "(no comment)" : title)
                    .subtitle(r.getRating() + "★")
                    .extra(extra)
                    .build();
        }).toList();
        return SearchGroup.builder().items(items).total(page.getTotalElements()).build();
    }

    private static String joinName(String first, String last) {
        StringBuilder sb = new StringBuilder();
        if (first != null && !first.isBlank()) sb.append(first.trim());
        if (last != null && !last.isBlank()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(last.trim());
        }
        return sb.toString();
    }
}
