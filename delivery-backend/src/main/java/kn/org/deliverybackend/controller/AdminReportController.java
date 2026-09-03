package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.dto.report.PeriodReportDTO;
import kn.org.deliverybackend.dto.report.RevenuePointDTO;
import kn.org.deliverybackend.dto.report.SalesSummaryDTO;
import kn.org.deliverybackend.dto.report.TopCategoryDTO;
import kn.org.deliverybackend.dto.report.TopProductDTO;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.reporting.PeriodType;
import kn.org.deliverybackend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin reporting. Dates are business-local calendar dates (Asia/Dhaka by
 * default) and {@code to} is inclusive.
 */
@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Admin: Reports")
public class AdminReportController {

    private final ReportService reportService;

    /**
     * The complete report for the day / week / month / year / fiscal year
     * containing {@code date} (today when omitted), with the previous period
     * for comparison.
     *
     * <pre>GET /admin/reports/period?type=WEEK&amp;date=2026-09-04</pre>
     */
    @GetMapping("/period")
    public ResponseEntity<PeriodReportDTO> period(
            @RequestParam(defaultValue = "DAY") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        PeriodType periodType = PeriodType.parse(type)
                .orElseThrow(() -> new InvalidStockOperationException(
                        "Unknown period type: " + type + " (expected DAY, WEEK, MONTH, YEAR or FISCAL_YEAR)"));
        return ResponseEntity.ok(reportService.periodReport(periodType, date));
    }

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
            g = ReportService.Granularity.valueOf(granularity.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidStockOperationException(
                    "Unknown granularity: " + granularity + " (expected DAY, WEEK, MONTH or YEAR)");
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
