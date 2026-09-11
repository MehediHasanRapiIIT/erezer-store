package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.request.product.PriceChangeRequestDTO;
import kn.org.deliverybackend.dto.response.product.PriceChangePreviewDTO;

/** Changing prices across a category (CATEGORY-PRICE-PLAN.md). */
public interface PriceChangeService {

    /**
     * What the change would do, one page at a time. {@code q} searches product
     * name and SKU; the counts and the changeable products cover the whole
     * category whatever the page or search. Saves nothing.
     */
    PriceChangePreviewDTO preview(PriceChangeRequestDTO request, String q, int page, int size);

    /**
     * Applies the change to the ticked products, all together or not at all.
     * Refused when a ticked product has left the category or any new price
     * would be ৳0 or less.
     */
    PriceChangePreviewDTO apply(PriceChangeRequestDTO request);
}
