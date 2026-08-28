package kn.org.deliverybackend.dto.response.search;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminSearchResponse {
    private String query;
    private SearchGroup products;
    private SearchGroup categories;
    private SearchGroup orders;
    private SearchGroup customers;
    private SearchGroup riders;
    private SearchGroup reviews;
    private long totalCount;
}
