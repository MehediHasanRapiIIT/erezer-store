package kn.org.deliverybackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The in-store showcase screen (/showcase), set on the Settings page: on or off,
 * seconds per slide, which products it shows, and the QR codes. One row (id 1).
 */
@Entity
@Table(name = "showcase_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShowcaseSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "slide_seconds", nullable = false)
    private Integer slideSeconds = 6;

    /** NEW (new arrivals, then the rest), ALL, or PICKED (only productIds). */
    @Column(name = "product_mode", nullable = false, length = 20)
    private String productMode = "NEW";

    /** The ticked products, in order, as "12,7,30"; used when productMode is PICKED. */
    @Column(name = "product_ids", columnDefinition = "text")
    private String productIds;

    @Column(name = "show_qr", nullable = false)
    private Boolean showQr = Boolean.TRUE;
}
