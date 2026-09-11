package kn.org.deliverybackend.dto.request.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kn.org.deliverybackend.service.CategoryCodeChange.CodeMode;

import java.util.List;

/**
 * Giving one category its product codes. {@code code} is the code itself when
 * {@code mode} is SAME (every ticked product gets it), and the prefix when it
 * is NUMBERED. {@code excludedProductIds} are the products the person unticked,
 * which keep the codes they have.
 */
public record CodeChangeRequestDTO(
        @NotNull Long categoryId,
        @NotNull CodeMode mode,
        @NotBlank String code,
        List<Long> excludedProductIds) {
}
