package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.dto.request.product.CodeChangeRequestDTO;
import kn.org.deliverybackend.dto.response.product.CodeChangePreviewDTO;
import kn.org.deliverybackend.dto.response.product.CodeChangePreviewDTO.Row;
import kn.org.deliverybackend.service.CategoryCodeChange.CodeMode;
import kn.org.deliverybackend.service.CodeChangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Giving a whole category its product codes (PRODUCT-CODE-PLAN.md, part 2).
 * Needs the same permission as changing one product's code: "Edit products".
 */
@RestController
@RequestMapping("/admin/products/code-change")
@RequiredArgsConstructor
@Tag(name = "Admin: Products")
public class AdminCodeChangeController {

    private final CodeChangeService codeChangeService;

    /**
     * What the change would do, one page at a time. {@code code} is the code
     * itself (mode SAME) or the prefix (mode NUMBERED); {@code q} searches
     * product name and code, and {@code excluded} are the unticked products.
     * A read, so trying codes isn't logged.
     */
    @RequiresPermission(Perm.PRODUCTS_EDIT)
    @GetMapping("/preview")
    public CodeChangePreviewDTO preview(
            @RequestParam Long categoryId,
            @RequestParam(defaultValue = "SAME") CodeMode mode,
            @RequestParam String code,
            @RequestParam(required = false) List<Long> excluded,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return codeChangeService.preview(new CodeChangeRequestDTO(categoryId, mode, code, excluded), q, page, size);
    }

    /** Gives the ticked products their new codes; the activity log keeps every old and new code. */
    @RequiresPermission(Perm.PRODUCTS_EDIT)
    @PostMapping
    public CodeChangePreviewDTO apply(@Valid @RequestBody CodeChangeRequestDTO request) {
        CodeChangePreviewDTO result = codeChangeService.apply(request);
        StaffAccess.describe("Changed product codes in " + result.categoryName() + " ("
                + result.changedCount() + " of " + result.productCount() + " products): "
                + (request.mode() == CodeMode.SAME ? "code " : "prefix ") + request.code().trim());
        StaffAccess.details(result.rows().stream()
                .filter(Row::changed)
                .map(row -> row.name() + ": code " + row.oldCode() + " → " + row.newCode())
                .collect(Collectors.joining("\n")));
        return result;
    }
}
