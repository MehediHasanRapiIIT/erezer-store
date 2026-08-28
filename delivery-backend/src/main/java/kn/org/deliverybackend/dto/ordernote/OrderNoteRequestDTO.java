package kn.org.deliverybackend.dto.ordernote;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderNoteRequestDTO {

    @NotBlank
    @Size(max = 4000)
    private String body;
}
