package kn.org.deliverybackend.dto.response.search;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CustomerSearchResponse {
    private String query;
    private SearchGroup products;
    private SearchGroup categories;
    private long totalCount;
}
