package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One reply the shop sent to a support message, from the admin panel. */
@Entity
@Table(name = "contact_reply")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ContactReply extends AbstractBaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The support message this answers. */
    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(nullable = false, length = 4000)
    private String body;

    /** Who sent it, as they were at the time. */
    @Column(name = "sent_by_id")
    private UUID sentById;

    @Column(name = "sent_by_name", length = 200)
    private String sentByName;

    /** The address it went to: the customer's, as it was on the message. */
    @Column(name = "sent_to", nullable = false, length = 255)
    private String sentTo;
}
