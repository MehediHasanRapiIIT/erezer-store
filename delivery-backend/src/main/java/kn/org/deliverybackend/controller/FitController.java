package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.service.impl.FitCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The shop's fits. Reading the offered ones is open to everyone (the shop and
 * every product form need them); the full list with hidden fits, and every
 * change, belong to the Size Charts page and need "Edit the size chart".
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Fits")
public class FitController {

    private final FitCatalog fits;

    /** The body of a create or an update. On an update, a missing name or hidden flag is left as it is. */
    public record FitRequest(String name, Long sizeChartId, Boolean hidden) {
    }

    public record FitOrderRequest(List<Long> fitIds) {
    }

    /**
     * Every fit, hidden ones marked: a product already in a hidden fit still has
     * to be shown by its name. Forms offer only the ones that are not hidden.
     */
    @GetMapping("/api/fits")
    public List<FitCatalog.FitView> offered() {
        return fits.list(true, false);
    }

    @RequiresPermission(value = {Perm.SETTINGS_VIEW, Perm.SETTINGS_SIZECHART}, mode = RequiresPermission.Mode.ANY)
    @GetMapping("/admin/fits")
    public List<FitCatalog.FitView> all() {
        return fits.list(true);
    }

    @RequiresPermission(Perm.SETTINGS_SIZECHART)
    @PostMapping("/admin/fits")
    public ResponseEntity<FitCatalog.FitView> create(@RequestBody FitRequest request) {
        FitCatalog.FitView made = fits.create(request == null ? null : request.name(), request == null ? null : request.sizeChartId());
        StaffAccess.describe("Added the fit " + made.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(made);
    }

    @RequiresPermission(Perm.SETTINGS_SIZECHART)
    @PutMapping("/admin/fits/{id}")
    public FitCatalog.FitView update(@PathVariable Long id, @RequestBody FitRequest request) {
        FitCatalog.FitView saved = fits.update(id, request.name(), request.sizeChartId(), request.hidden());
        StaffAccess.describe("Changed the fit " + saved.name());
        return saved;
    }

    @RequiresPermission(Perm.SETTINGS_SIZECHART)
    @PutMapping("/admin/fits/order")
    public List<FitCatalog.FitView> reorder(@RequestBody FitOrderRequest request) {
        List<FitCatalog.FitView> all = fits.reorder(request == null ? null : request.fitIds());
        StaffAccess.describe("Changed the order of fits");
        return all;
    }

    @RequiresPermission(Perm.SETTINGS_SIZECHART)
    @DeleteMapping("/admin/fits/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        fits.delete(id);
        StaffAccess.describe("Removed a fit");
        return ResponseEntity.noContent().build();
    }
}
