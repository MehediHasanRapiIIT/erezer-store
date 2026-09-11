package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.request.product.CodeChangeRequestDTO;
import kn.org.deliverybackend.dto.response.product.CodeChangePreviewDTO;

/** Giving a whole category its product codes (PRODUCT-CODE-PLAN.md, part 2). */
public interface CodeChangeService {

    /**
     * What the change would do, one page at a time. {@code q} searches product
     * name and code; the counts cover the whole category whatever the page or
     * search. Saves nothing.
     */
    CodeChangePreviewDTO preview(CodeChangeRequestDTO request, String q, int page, int size);

    /** Gives the ticked products their new codes, all together or not at all. */
    CodeChangePreviewDTO apply(CodeChangeRequestDTO request);
}
