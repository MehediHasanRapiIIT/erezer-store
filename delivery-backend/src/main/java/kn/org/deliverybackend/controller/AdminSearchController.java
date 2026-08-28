package kn.org.deliverybackend.controller;

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

    @GetMapping
    public ResponseEntity<AdminSearchResponse> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return ResponseEntity.ok(adminSearchService.search(query, limit));
    }
}
