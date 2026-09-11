package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffManagementService;
import kn.org.deliverybackend.dto.staff.PermissionTemplateDTO;
import kn.org.deliverybackend.dto.staff.PermissionTemplateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Saved sets of permissions ("Order handler", "Packer"), applied on the Staff page. */
@RestController
@RequestMapping("/admin/permission-templates")
@RequiredArgsConstructor
@Tag(name = "Admin: Staff")
public class AdminPermissionTemplateController {

    private final StaffManagementService staffService;

    @GetMapping
    @RequiresPermission(value = {Perm.STAFF_VIEW, Perm.STAFF_PERMISSIONS}, mode = RequiresPermission.Mode.ANY)
    public List<PermissionTemplateDTO> list() {
        return staffService.templates();
    }

    @PostMapping
    @RequiresPermission(Perm.STAFF_PERMISSIONS)
    public ResponseEntity<PermissionTemplateDTO> create(@Valid @RequestBody PermissionTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.createTemplate(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(Perm.STAFF_PERMISSIONS)
    public PermissionTemplateDTO update(@PathVariable UUID id, @Valid @RequestBody PermissionTemplateRequest request) {
        return staffService.updateTemplate(id, request);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(Perm.STAFF_PERMISSIONS)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        staffService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }
}
