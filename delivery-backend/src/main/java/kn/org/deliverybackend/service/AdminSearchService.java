package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.response.search.AdminSearchResponse;

public interface AdminSearchService {
    AdminSearchResponse search(String query, int limit);
}
