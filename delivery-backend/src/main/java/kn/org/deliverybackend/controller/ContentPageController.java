package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.dto.settings.ContentPageDTO;
import kn.org.deliverybackend.service.impl.ContentPageService;
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
 * The shop's own pages: "Our Mission", "Our Values", and any other it writes.
 *
 * <p>Customers read the ones that are switched on. Writing them is part of
 * looking after the shop's About content, so it needs that permission.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Pages")
public class ContentPageController {

    private final ContentPageService pages;

    @GetMapping("/api/pages")
    public List<ContentPageDTO> list() {
        return pages.listActive();
    }

    @GetMapping("/api/pages/{slug}")
    public ContentPageDTO get(@PathVariable String slug) {
        return pages.getActive(slug);
    }

    @RequiresPermission(value = {Perm.SETTINGS_VIEW, Perm.SETTINGS_ABOUT}, mode = RequiresPermission.Mode.ANY)
    @GetMapping("/admin/pages")
    public List<ContentPageDTO> listAll() {
        return pages.listAll();
    }

    @RequiresPermission(Perm.SETTINGS_ABOUT)
    @PostMapping("/admin/pages")
    public ResponseEntity<ContentPageDTO> create(@Valid @RequestBody ContentPageDTO request) {
        ContentPageDTO created = pages.create(request);
        StaffAccess.describe("Added the page " + created.getTitle());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @RequiresPermission(Perm.SETTINGS_ABOUT)
    @PutMapping("/admin/pages/{id}")
    public ResponseEntity<ContentPageDTO> update(@PathVariable Long id, @Valid @RequestBody ContentPageDTO request) {
        ContentPageDTO updated = pages.update(id, request);
        StaffAccess.describe("Changed the page " + updated.getTitle());
        return ResponseEntity.ok(updated);
    }

    @RequiresPermission(Perm.SETTINGS_ABOUT)
    @DeleteMapping("/admin/pages/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        pages.delete(id);
        StaffAccess.describe("Deleted a page");
        return ResponseEntity.noContent().build();
    }
}
