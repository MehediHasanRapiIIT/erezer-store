package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.access.ActivityLogService;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.dto.staff.ActivityPageDTO;
import kn.org.deliverybackend.dto.staff.ActivityPersonDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The activity log: who changed what in the admin panel, and when. */
@RestController
@RequestMapping("/admin/activity")
@RequiredArgsConstructor
@Tag(name = "Admin: Activity log")
@RequiresPermission(Perm.ACTIVITY_VIEW)
public class AdminActivityController {

    private final ActivityLogService activityLog;

    /** Newest first. {@code from} and {@code to} are shop-local dates, both inclusive. */
    @GetMapping
    public ActivityPageDTO search(
            @RequestParam(required = false) UUID staffId,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return activityLog.search(staffId, area, from, to, page, size);
    }

    @GetMapping("/people")
    public List<ActivityPersonDTO> people() {
        return activityLog.people();
    }
}
