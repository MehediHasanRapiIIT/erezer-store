package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.response.search.CustomerSearchResponse;

public interface CustomerSearchService {
    CustomerSearchResponse search(String query, int limit);
}
