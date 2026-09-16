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

@RestController
@RequestMapping("/admin/customers")
@RequiredArgsConstructor
@Tag(name = "Admin: Customers")
public class AdminCustomerController {

    private final ReportService reportService;

    /**
     * Every customer, a page at a time: each account (with or without orders) and
     * each guest shopper by email, ranked by lifetime revenue. Cancelled and
     * returned orders are left out of the totals (see {@code ReportRepository.customerPage}).
     * {@code q} searches the customer's name, email and phone.
     */
    @RequiresPermission(Perm.CUSTOMERS_VIEW)
    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<CustomerLifetimeValueDTO>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        var customers = reportService.customers(q, page, size);
        // Lifetime value is a money total: left out without "See money totals".
        if (!StaffAccess.has(Perm.FINANCE_REVENUE)) {
            customers.getContent().forEach(c -> {
                c.setLifetimeRevenue(null);
                c.setAverageOrderValue(null);
            });
        }
        return ResponseEntity.ok(customers);
    }
}
