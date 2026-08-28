package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.response.search.CustomerSearchResponse;
import kn.org.deliverybackend.dto.response.search.SearchGroup;
import kn.org.deliverybackend.dto.response.search.SearchItem;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.service.CustomerSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomerSearchServiceImpl implements CustomerSearchService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public CustomerSearchResponse search(String query, int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return CustomerSearchResponse.builder()
                    .query(q)
                    .products(SearchGroup.empty())
                    .categories(SearchGroup.empty())
                    .totalCount(0L)
                    .build();
        }

        int safeLimit = Math.min(Math.max(limit, 1), 50);
        Pageable pageable = PageRequest.of(0, safeLimit);

        // Single-brand clothing store: only products + categories are relevant.
        // (Shops & banners were vestigial from the delivery-app origin.)
        SearchGroup products = toProducts(productRepository.searchCustomer(q, pageable));
        SearchGroup categories = toCategories(categoryRepository.searchCustomer(q, pageable));

        long total = products.getTotal() + categories.getTotal();

        return CustomerSearchResponse.builder()
                .query(q)
                .products(products)
                .categories(categories)
                .totalCount(total)
                .build();
    }

    private SearchGroup toProducts(Page<Product> page) {
        List<SearchItem> items = page.getContent().stream().map(p -> {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("price", p.getPrice());
            if (p.getDiscountPrice() != null) extra.put("discountPrice", p.getDiscountPrice());
            extra.put("avgRating", p.getAvgRating());
            extra.put("totalReviews", p.getTotalReviews());
            if (p.getUnit() != null) extra.put("unit", p.getUnit());
            return SearchItem.builder()
                    .id(String.valueOf(p.getId()))
                    .type("PRODUCT")
                    .title(p.getName())
                    .subtitle(p.getDescription())
                    .imageUrl(p.getImageUrl())
                    .extra(extra)
                    .build();
        }).toList();
        return SearchGroup.builder().items(items).total(page.getTotalElements()).build();
    }

    private SearchGroup toCategories(Page<Category> page) {
        List<SearchItem> items = page.getContent().stream().map(c -> SearchItem.builder()
                .id(String.valueOf(c.getId()))
                .type("CATEGORY")
                .title(c.getName())
                .build()).toList();
        return SearchGroup.builder().items(items).total(page.getTotalElements()).build();
    }
}
