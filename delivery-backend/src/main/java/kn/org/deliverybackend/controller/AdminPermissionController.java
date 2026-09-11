package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.access.AnyStaff;
import kn.org.deliverybackend.repository.PermissionDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Every permission with its area and plain-language name, so the admin panel
 * can say "Cancel orders" instead of "orders.cancel". Not secret: any staff
 * login may read it.
 */
@RestController
@RequestMapping("/admin/permissions")
@RequiredArgsConstructor
@Tag(name = "Admin: Me")
public class AdminPermissionController {

    private final PermissionDefinitionRepository repository;

    public record PermissionInfo(String key, String area, String label, String description) {}

    @GetMapping
    @AnyStaff
    public List<PermissionInfo> list() {
        return repository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(p -> new PermissionInfo(p.getKey(), p.getArea(), p.getLabel(), p.getDescription()))
                .toList();
    }
}
