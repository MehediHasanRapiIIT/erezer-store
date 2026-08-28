package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import kn.org.deliverybackend.enumeration.CustomOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A custom-design quote request ("Submit for Price"). Captures the customer's
 * contact + shipping details, their free-text order notes (which carry the
 * required size-wise quantity and print/embroidery technique), a snapshot of
 * the chosen garment/colour, the re-editable fabric.js design JSON, and the
 * flattened per-view preview images. On submit the admin is emailed and this
 * row appears in the admin panel; pricing is handled off-platform.
 */
@Entity
@Table(name = "custom_order",
        indexes = {
                @Index(name = "idx_custom_order_status", columnList = "status, created_at DESC"),
                @Index(name = "idx_custom_order_email", columnList = "email"),
                @Index(name = "idx_custom_order_user", columnList = "user_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomOrder extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Human-friendly reference shown to the customer and in the admin panel. */
    @Column(nullable = false, length = 20, unique = true)
    private String reference;

    /** Null for guest submissions; set when a logged-in customer submits. */
    @Column(name = "user_id")
    private UUID userId;

    // ── Contact ─────────────────────────────────────────────────────────────
    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(nullable = false, length = 255)
    private String email;

    // ── Shipping ────────────────────────────────────────────────────────────
    @Column(name = "shipping_address", nullable = false, length = 500)
    private String shippingAddress;

    @Column(length = 255)
    private String apartment;

    @Column(nullable = false, length = 150)
    private String city;

    @Column(name = "zip_code", length = 30)
    private String zipCode;

    @Column(nullable = false, length = 120)
    private String country;

    // ── Order details ───────────────────────────────────────────────────────
    /** Rich-text (HTML) notes: required size-wise quantity + print/embroidery technique. */
    @Column(nullable = false, columnDefinition = "text")
    private String notes;

    /** Snapshot of studio selections for quick admin scanning (design JSON is authoritative). */
    @Column(name = "item_name", length = 150)
    private String itemName;

    @Column(name = "color_name", length = 80)
    private String colorName;

    @Column(name = "size", length = 40)
    private String size;

    @Column(name = "print_technique", length = 80)
    private String printTechnique;

    /** Re-editable fabric.js canvas state (per-view JSON), so a design can be reopened. */
    @Column(name = "design_json", columnDefinition = "text")
    private String designJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CustomOrderStatus status = CustomOrderStatus.NEW;

    /** Internal staff notes, not shown to the customer. */
    @Column(name = "admin_notes", columnDefinition = "text")
    private String adminNotes;

    @OneToMany(mappedBy = "customOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<CustomOrderImage> images = new ArrayList<>();

    public void addImage(CustomOrderImage image) {
        image.setCustomOrder(this);
        this.images.add(image);
    }
}
