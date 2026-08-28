package kn.org.deliverybackend.dto.invoice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceSendResultDTO {
    private boolean emailSent;
    private boolean smsSent;
    private String message;
}
