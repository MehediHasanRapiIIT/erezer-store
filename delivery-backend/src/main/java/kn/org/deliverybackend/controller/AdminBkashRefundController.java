package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.payment.bkash.BkashRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/** Refunds of a bKash order, on the admin order page: what is left to refund, and refunding it. */
@RestController
@RequestMapping("/admin/orders/{orderId}/bkash-refunds")
@RequiredArgsConstructor
@Tag(name = "Admin: bKash refunds")
public class AdminBkashRefundController {

    private final BkashRefundService refunds;

    public record RefundRequest(BigDecimal amount, String reason) {
    }

    @RequiresPermission(Perm.ORDERS_VIEW)
    @GetMapping
    public ResponseEntity<BkashRefundService.Summary> get(@PathVariable UUID orderId) {
        return ResponseEntity.ok(refunds.summary(orderId));
    }

    /** Sends money back to the customer's bKash wallet. The same permission as refunding a return. */
    @RequiresPermission(Perm.RETURNS_REFUND)
    @PostMapping
    public ResponseEntity<BkashRefundService.Summary> refund(@PathVariable UUID orderId, @RequestBody RefundRequest request,
                                                             @AuthenticationPrincipal Jwt jwt) {
        String who = jwt == null ? "admin" : "admin:" + (jwt.getClaimAsString("preferred_username") != null
                ? jwt.getClaimAsString("preferred_username") : jwt.getSubject());
        var done = refunds.refund(orderId, request == null ? null : request.amount(),
                request == null ? null : request.reason(), who, null);
        StaffAccess.describe("Refunded ৳" + done.getAmount().toPlainString() + " to bKash (" + done.getRefundTrxId() + ")");
        return ResponseEntity.ok(refunds.summary(orderId));
    }
}
