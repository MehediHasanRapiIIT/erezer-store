package kn.org.deliverybackend.dto.contact;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A reply to a support message: what is sent to write one (the body), and what is read back. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactReplyDTO {
    private Long id;
    @NotBlank(message = "Write your reply first")
    @Size(max = 4000, message = "A reply can be up to 4000 letters")
    private String body;
    /** Who sent it. */
    private String sentByName;
    private LocalDateTime sentAt;
    /** The address it went to. */
    private String sentTo;
}
