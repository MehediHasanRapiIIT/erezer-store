package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.dto.response.search.CustomerSearchResponse;
import kn.org.deliverybackend.service.CustomerSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@CrossOrigin("*")
public class CustomerSearchController {

    private final CustomerSearchService customerSearchService;

    @GetMapping
    public ResponseEntity<CustomerSearchResponse> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return ResponseEntity.ok(customerSearchService.search(query, limit));
    }
}
