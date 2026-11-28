package kn.org.deliverybackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** An order that reached Erezer Invoice: when, by whom, and its number there. The latest send only. */
@Entity
@Table(name = "order_invoice_send")
@Getter
@Setter
@NoArgsConstructor
public class OrderInvoiceSend {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    /** The order's id in Erezer Invoice. */
    @Column(name = "invoice_order_id")
    private Long invoiceOrderId;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "sent_by_name", length = 150)
    private String sentByName;

    /** Photos that couldn't go with it (none stored, unreadable, refused there). */
    @Column(name = "missing_photos", nullable = false)
    private int missingPhotos;
}
