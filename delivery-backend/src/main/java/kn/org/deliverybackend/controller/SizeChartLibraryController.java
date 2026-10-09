package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.dto.settings.SizeChartLibraryDTO;
import kn.org.deliverybackend.service.impl.SizeChartLibraryService;
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
import java.util.Map;

/**
 * The size chart library.
 *
 * <p>Reading it is open to everyone: the shop shows these charts to customers,
 * and every admin form that names a chart needs the list. Changing it needs
 * "Edit the size chart".
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Size charts")
public class SizeChartLibraryController {

    private final SizeChartLibraryService sizeCharts;

    @GetMapping("/api/size-charts")
    public List<SizeChartLibraryDTO> list() {
        return sizeCharts.list();
    }

    @RequiresPermission(Perm.SETTINGS_SIZECHART)
    @PostMapping("/admin/size-charts")
    public ResponseEntity<SizeChartLibraryDTO> create(@Valid @RequestBody SizeChartLibraryDTO request) {
        SizeChartLibraryDTO created = sizeCharts.create(request);
        StaffAccess.describe("Added the size chart " + created.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @RequiresPermission(Perm.SETTINGS_SIZECHART)
    @PutMapping("/admin/size-charts/{id}")
    public ResponseEntity<SizeChartLibraryDTO> update(@PathVariable Long id, @Valid @RequestBody SizeChartLibraryDTO request) {
        SizeChartLibraryDTO updated = sizeCharts.update(id, request);
        StaffAccess.describe("Changed the size chart " + updated.getName());
        return ResponseEntity.ok(updated);
    }

    @RequiresPermission(Perm.SETTINGS_SIZECHART)
    @PutMapping("/admin/size-charts/{id}/default")
    public ResponseEntity<SizeChartLibraryDTO> setDefault(@PathVariable Long id) {
        SizeChartLibraryDTO chart = sizeCharts.setDefault(id);
        StaffAccess.describe("Made " + chart.getName() + " the default size chart");
        return ResponseEntity.ok(chart);
    }

    /** Answers with how many products and categories were using it and now follow their category or the default. */
    @RequiresPermission(Perm.SETTINGS_SIZECHART)
    @DeleteMapping("/admin/size-charts/{id}")
    public ResponseEntity<Map<String, Long>> delete(@PathVariable Long id) {
        long released = sizeCharts.delete(id);
        StaffAccess.describe("Deleted a size chart" + (released > 0 ? " that " + released + " products or categories used" : ""));
        return ResponseEntity.ok(Map.of("released", released));
    }
}
