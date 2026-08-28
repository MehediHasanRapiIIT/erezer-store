package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.dto.report.RevenuePointDTO;
import kn.org.deliverybackend.dto.report.SalesSummaryDTO;
import kn.org.deliverybackend.dto.report.TopCategoryDTO;
import kn.org.deliverybackend.dto.report.TopProductDTO;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Admin: Reports")
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping("/summary")
    public ResponseEntity<SalesSummaryDTO> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.summary(from, to));
    }

    @GetMapping("/revenue")
    public ResponseEntity<List<RevenuePointDTO>> revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") String granularity) {
        ReportService.Granularity g;
        try {
            g = ReportService.Granularity.valueOf(granularity.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidStockOperationException("Unknown granularity: " + granularity);
        }
        return ResponseEntity.ok(reportService.revenueTimeseries(from, to, g));
    }

    @GetMapping("/top-products")
    public ResponseEntity<List<TopProductDTO>> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(reportService.topProducts(from, to, limit));
    }

    @GetMapping("/top-categories")
    public ResponseEntity<List<TopCategoryDTO>> topCategories(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(reportService.topCategories(from, to, limit));
    }
}
