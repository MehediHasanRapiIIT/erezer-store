package kn.org.deliverybackend.dto.returns;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReturnItemRequestDTO {

    @NotNull
    private UUID orderItemId;

    @NotNull
    @Positive
    private Integer quantity;

    /** SEALED | OPENED | DAMAGED | OTHER */
    private String condition;
}
