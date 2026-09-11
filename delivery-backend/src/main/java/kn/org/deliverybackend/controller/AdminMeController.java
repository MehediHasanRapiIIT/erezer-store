package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.access.AnyStaff;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.access.StaffView;
import kn.org.deliverybackend.exception.PermissionDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Who the logged-in staff member is and what they may do. The admin panel
 * reads this after login and again whenever a request is refused, to decide
 * which pages, buttons and figures to show.
 */
@RestController
@RequestMapping("/admin/me")
@Tag(name = "Admin: Me")
public class AdminMeController {

    @GetMapping
    @AnyStaff
    public Map<String, Object> me() {
        StaffView staff = StaffAccess.current()
                .orElseThrow(() -> new PermissionDeniedException("Not a staff login."));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", staff.id());
        body.put("username", staff.username());
        body.put("email", staff.email());
        body.put("name", staff.name());
        body.put("role", staff.role().name());
        body.put("admin", staff.isAdmin());
        body.put("permissions", new TreeSet<>(staff.effectivePermissions()));
        return body;
    }
}
