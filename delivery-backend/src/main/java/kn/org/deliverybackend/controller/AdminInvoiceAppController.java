package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.access.StaffView;
import kn.org.deliverybackend.dto.auth.MessageResponseDTO;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.Change;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.Outcome;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.SendRequest;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.SendResult;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.SentMark;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.Settings;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppSender;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Erezer Invoice, the owner's separate order system: its address and key
 * (Settings), and sending chosen orders there (Orders). The key is write-only:
 * it goes in from here and is never sent back.
 */
@RestController
@RequestMapping("/admin/invoice-app")
@RequiredArgsConstructor
@Tag(name = "Admin: Erezer Invoice")
public class AdminInvoiceAppController {

    private final InvoiceAppSettingsService settings;
    private final InvoiceAppSender sender;

    @RequiresPermission(Perm.SETTINGS_INVOICE_APP)
    @GetMapping
    public ResponseEntity<Settings> get() {
        return ResponseEntity.ok(settings.get());
    }

    @RequiresPermission(Perm.SETTINGS_INVOICE_APP)
    @PutMapping
    public ResponseEntity<Settings> update(@Valid @RequestBody Change change) {
        Settings saved = settings.update(change);
        // The log says what changed, never the key itself.
        StringBuilder what = new StringBuilder("Erezer Invoice: ");
        what.append(saved.url() == null ? "no address" : saved.url());
        if (Boolean.TRUE.equals(change.removeKey())) what.append(", key removed");
        else if (change.key() != null && !change.key().isBlank()) what.append(", new key saved");
        StaffAccess.describe(what.toString());
        return ResponseEntity.ok(saved);
    }

    /** Asks Erezer Invoice whether it accepts the saved key. Changes nothing there. */
    @RequiresPermission(Perm.SETTINGS_INVOICE_APP)
    @PostMapping("/test")
    public ResponseEntity<MessageResponseDTO> test() {
        StaffAccess.describe("Tested the Erezer Invoice connection");
        sender.test();
        return ResponseEntity.ok(MessageResponseDTO.of("Connected: Erezer Invoice accepted the key."));
    }

    /** For the order screens: whether there is anywhere to send to. */
    @RequiresPermission(Perm.ORDERS_SEND_INVOICE_APP)
    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status() {
        return ResponseEntity.ok(Map.of("connected", settings.get().connected()));
    }

    /** Sends the chosen orders (at most 50), with their photos. One result per order. */
    @RequiresPermission(Perm.ORDERS_SEND_INVOICE_APP)
    @PostMapping("/send")
    public ResponseEntity<List<SendResult>> send(@Valid @RequestBody SendRequest request) {
        String who = StaffAccess.current().map(StaffView::name).filter(Objects::nonNull).orElse("Staff");
        List<SendResult> results = sender.send(request.orderIds(), who);

        long sent = results.stream().filter(r -> r.outcome() == Outcome.SENT).count();
        long there = results.stream().filter(r -> r.outcome() == Outcome.ALREADY_THERE).count();
        long failed = results.stream().filter(r -> r.outcome() == Outcome.FAILED).count();
        StaffAccess.describe("Sent orders to Erezer Invoice: " + sent + " sent"
                + (there > 0 ? ", " + there + " already there" : "")
                + (failed > 0 ? ", " + failed + " not sent" : ""));
        StaffAccess.details(results.stream()
                .map(r -> (r.orderNumber() == null ? String.valueOf(r.orderId()) : r.orderNumber()) + ": "
                        + switch (r.outcome()) {
                            case SENT -> "sent";
                            case ALREADY_THERE -> "already there";
                            case FAILED -> "not sent (" + r.message() + ")";
                        })
                .collect(Collectors.joining("\n")));
        return ResponseEntity.ok(results);
    }

    /** "In Invoice ✓" for a page of the order list: ?ids=a,b,c. Orders never sent are left out. */
    @RequiresPermission(Perm.ORDERS_VIEW)
    @GetMapping("/sent")
    public ResponseEntity<Map<UUID, SentMark>> sent(@RequestParam List<UUID> ids) {
        if (ids.size() > 200) ids = ids.subList(0, 200);
        return ResponseEntity.ok(sender.marks(ids));
    }
}
