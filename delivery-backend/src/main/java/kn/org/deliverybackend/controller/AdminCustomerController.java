package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.dto.report.CustomerLifetimeValueDTO;
import kn.org.deliverybackend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/customers")
@RequiredArgsConstructor
@Tag(name = "Admin: Customers")
public class AdminCustomerController {

    private final ReportService reportService;

    /**
     * Customer roster ranked by lifetime revenue. Cancelled and returned orders
     * are excluded from the totals (see {@code ReportRepository.customerLifetimeValue}).
     */
    @GetMapping
    public ResponseEntity<List<CustomerLifetimeValueDTO>> list(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0")  int offset) {
        return ResponseEntity.ok(reportService.customerLtv(limit, offset));
    }

    @GetMapping("/count")
    public ResponseEntity<Long> totalCount() {
        return ResponseEntity.ok(reportService.totalCustomersWithOrders());
    }
}
