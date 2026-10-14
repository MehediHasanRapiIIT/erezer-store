package kn.org.deliverybackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the customer emails show, set on the Settings page: where a customer can
 * leave a review once the order is delivered, the shop's address for the footer,
 * and whether the cart reminder and newsletter emails may go out. One row (id 1).
 */
@Entity
@Table(name = "customer_email_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEmailSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "review_on_website", nullable = false)
    private Boolean reviewOnWebsite = Boolean.TRUE;

    @Column(name = "facebook_url", length = 300)
    private String facebookUrl;

    @Column(name = "instagram_url", length = 300)
    private String instagramUrl;

    @Column(name = "whatsapp_number", length = 40)
    private String whatsappNumber;

    @Column(name = "business_address", length = 300)
    private String businessAddress;

    @Column(name = "cart_reminder_enabled", nullable = false)
    private Boolean cartReminderEnabled = Boolean.FALSE;

    @Column(name = "newsletter_enabled", nullable = false)
    private Boolean newsletterEnabled = Boolean.FALSE;
}
