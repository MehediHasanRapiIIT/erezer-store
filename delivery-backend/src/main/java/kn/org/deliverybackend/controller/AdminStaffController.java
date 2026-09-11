package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.access.AdminOnly;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffManagementService;
import kn.org.deliverybackend.dto.staff.PasswordResetRequest;
import kn.org.deliverybackend.dto.staff.StaffCreateRequest;
import kn.org.deliverybackend.dto.staff.StaffDTO;
import kn.org.deliverybackend.dto.staff.StaffPermissionsRequest;
import kn.org.deliverybackend.dto.staff.StaffRoleRequest;
import kn.org.deliverybackend.dto.staff.StaffUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * The Staff page. The safety rules (last admin, own account, delegation,
 * admins' accounts) live in {@link StaffManagementService}.
 */
@RestController
@RequestMapping("/admin/staff")
@RequiredArgsConstructor
@Tag(name = "Admin: Staff")
public class AdminStaffController {

    private final StaffManagementService staffService;

    @GetMapping
    @RequiresPermission(Perm.STAFF_VIEW)
    public List<StaffDTO> list() {
        return staffService.list();
    }

    @PostMapping
    @RequiresPermission(Perm.STAFF_MANAGE)
    public ResponseEntity<StaffDTO> create(@Valid @RequestBody StaffCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Perm.STAFF_MANAGE)
    public StaffDTO update(@PathVariable UUID id, @Valid @RequestBody StaffUpdateRequest request) {
        return staffService.update(id, request);
    }

    @PutMapping("/{id}/role")
    @AdminOnly
    public StaffDTO changeRole(@PathVariable UUID id, @Valid @RequestBody StaffRoleRequest request) {
        return staffService.changeRole(id, request.role());
    }

    @PutMapping("/{id}/permissions")
    @RequiresPermission(Perm.STAFF_PERMISSIONS)
    public StaffDTO setPermissions(@PathVariable UUID id, @Valid @RequestBody StaffPermissionsRequest request) {
        return staffService.setPermissions(id, request.permissions());
    }

    @PostMapping("/{id}/deactivate")
    @RequiresPermission(Perm.STAFF_MANAGE)
    public StaffDTO deactivate(@PathVariable UUID id) {
        return staffService.deactivate(id);
    }

    @PostMapping("/{id}/reactivate")
    @RequiresPermission(Perm.STAFF_MANAGE)
    public StaffDTO reactivate(@PathVariable UUID id) {
        return staffService.reactivate(id);
    }

    @PostMapping("/{id}/reset-password")
    @RequiresPermission(Perm.STAFF_MANAGE)
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id, @Valid @RequestBody PasswordResetRequest request) {
        staffService.resetPassword(id, request.temporaryPassword());
        return ResponseEntity.noContent().build();
    }

    /** {@code confirm} must be the person's username: deleting can't be undone. */
    @DeleteMapping("/{id}")
    @RequiresPermission(Perm.STAFF_DELETE)
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam(required = false) String confirm) {
        staffService.delete(id, confirm);
        return ResponseEntity.noContent().build();
    }
}
