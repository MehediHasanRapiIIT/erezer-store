package kn.org.deliverybackend.dto.returns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequestCreateDTO {

    /** WRONG_SIZE | DEFECTIVE | NOT_AS_DESCRIBED | CHANGED_MIND | OTHER */
    @NotBlank
    private String reason;

    @Size(max = 2000)
    private String customerNotes;

    @NotEmpty
    @Valid
    private List<ReturnItemRequestDTO> items;
}
