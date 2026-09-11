package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
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
     * {@code q} searches the customer's name, email and phone.
     */
    @RequiresPermission(Perm.CUSTOMERS_VIEW)
    @GetMapping
    public ResponseEntity<List<CustomerLifetimeValueDTO>> list(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0")  int offset,
            @RequestParam(required = false) String q) {
        List<CustomerLifetimeValueDTO> customers = reportService.customerLtv(limit, offset, q);
        // Lifetime value is a money total: left out without "See money totals".
        if (!StaffAccess.has(Perm.FINANCE_REVENUE)) {
            customers.forEach(c -> {
                c.setLifetimeRevenue(null);
                c.setAverageOrderValue(null);
            });
        }
        return ResponseEntity.ok(customers);
    }

    /** How many purchasing customers match {@code q}; all of them when it is blank. The same people the list shows. */
    @RequiresPermission(Perm.CUSTOMERS_VIEW)
    @GetMapping("/count")
    public ResponseEntity<Long> totalCount(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(reportService.customerCount(q));
    }
}
