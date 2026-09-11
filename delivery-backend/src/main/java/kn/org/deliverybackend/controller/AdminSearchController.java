package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.AnyStaff;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.access.StaffView;
import kn.org.deliverybackend.dto.response.search.SearchGroup;
import kn.org.deliverybackend.dto.response.search.AdminSearchResponse;
import kn.org.deliverybackend.service.AdminSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/search")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AdminSearchController {

    private final AdminSearchService adminSearchService;

    @AnyStaff
    @GetMapping
    public ResponseEntity<AdminSearchResponse> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return ResponseEntity.ok(visibleTo(adminSearchService.search(query, limit)));
    }

    /** Only the result groups for areas this person may see. Riders are admin-only. */
    private static AdminSearchResponse visibleTo(AdminSearchResponse all) {
        if (StaffAccess.current().map(StaffView::isAdmin).orElse(false)) return all;
        SearchGroup products = keep(all.getProducts(), Perm.PRODUCTS_VIEW);
        SearchGroup categories = keep(all.getCategories(), Perm.CATEGORIES_VIEW);
        SearchGroup orders = keep(all.getOrders(), Perm.ORDERS_VIEW);
        SearchGroup customers = keep(all.getCustomers(), Perm.CUSTOMERS_VIEW);
        SearchGroup reviews = keep(all.getReviews(), Perm.REVIEWS_VIEW);
        return AdminSearchResponse.builder()
                .query(all.getQuery())
                .products(products)
                .categories(categories)
                .orders(orders)
                .customers(customers)
                .riders(SearchGroup.empty())
                .reviews(reviews)
                .totalCount(products.getTotal() + categories.getTotal() + orders.getTotal()
                        + customers.getTotal() + reviews.getTotal())
                .build();
    }

    private static SearchGroup keep(SearchGroup group, Perm perm) {
        return group != null && StaffAccess.has(perm) ? group : SearchGroup.empty();
    }
}
