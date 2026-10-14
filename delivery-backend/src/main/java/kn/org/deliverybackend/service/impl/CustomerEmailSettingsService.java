package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.CustomerEmailSettings;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.CustomerEmailSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Locale;

/**
 * The Settings page's "Customer emails" section: where the "delivered" email
 * invites a review (the website, Facebook, Instagram, WhatsApp), the shop's
 * address in every email's footer, and the switches for the cart reminder and
 * newsletter emails, which stay off while order emails share one small mailbox.
 */
@Service
@RequiredArgsConstructor
public class CustomerEmailSettingsService {

    private final CustomerEmailSettingsRepository repository;

    /** The section as the admin page edits it. Empty text means "not shown". */
    public record Settings(boolean reviewOnWebsite, String facebookUrl, String instagramUrl, String whatsappNumber,
                           String businessAddress, boolean cartReminderEnabled, boolean newsletterEnabled) {
    }

    @Transactional(readOnly = true)
    public Settings get() {
        return toSettings(row());
    }

    @Transactional
    public Settings save(Settings change) {
        if (change == null) throw new InvalidRequestException("Nothing to save.");
        CustomerEmailSettings row = repository.findById(CustomerEmailSettings.SINGLETON_ID).orElseGet(CustomerEmailSettings::new);
        row.setReviewOnWebsite(change.reviewOnWebsite());
        row.setFacebookUrl(link(change.facebookUrl(), "Facebook"));
        row.setInstagramUrl(link(change.instagramUrl(), "Instagram"));
        row.setWhatsappNumber(whatsappNumber(change.whatsappNumber()));
        row.setBusinessAddress(text(change.businessAddress(), 300));
        row.setCartReminderEnabled(change.cartReminderEnabled());
        row.setNewsletterEnabled(change.newsletterEnabled());
        return toSettings(repository.save(row));
    }

    @Transactional(readOnly = true)
    public boolean cartReminderEnabled() {
        return Boolean.TRUE.equals(row().getCartReminderEnabled());
    }

    @Transactional(readOnly = true)
    public boolean newsletterEnabled() {
        return Boolean.TRUE.equals(row().getNewsletterEnabled());
    }

    /** The WhatsApp chat link for a number: 01712-345678 → https://wa.me/8801712345678. */
    public static String whatsappLink(String number) {
        if (number == null || number.isBlank()) return null;
        String digits = number.replaceAll("\\D", "");
        if (digits.startsWith("0")) digits = "88" + digits;
        return "https://wa.me/" + digits;
    }

    private CustomerEmailSettings row() {
        return repository.findById(CustomerEmailSettings.SINGLETON_ID).orElseGet(CustomerEmailSettings::new);
    }

    private static Settings toSettings(CustomerEmailSettings r) {
        return new Settings(!Boolean.FALSE.equals(r.getReviewOnWebsite()), blank(r.getFacebookUrl()), blank(r.getInstagramUrl()),
                blank(r.getWhatsappNumber()), blank(r.getBusinessAddress()),
                Boolean.TRUE.equals(r.getCartReminderEnabled()), Boolean.TRUE.equals(r.getNewsletterEnabled()));
    }

    // ── checks ────────────────────────────────────────────────────────────────

    /** A web address the email can link to: https only, so no email ever carries a doubtful link. */
    static String link(String value, String what) {
        String v = text(value, 300);
        if (v == null) return null;
        if (!v.toLowerCase(Locale.ROOT).startsWith("http")) v = "https://" + v;
        try {
            URI uri = URI.create(v);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || !uri.getHost().contains(".")) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("The " + what + " link has to be a web address that starts with https://, such as https://m.me/erezer.");
        }
        return v;
    }

    /** A Bangladeshi or international mobile number, kept as the admin typed it. */
    static String whatsappNumber(String value) {
        String v = text(value, 40);
        if (v == null) return null;
        String digits = v.replaceAll("\\D", "");
        if (!v.matches("[+\\d\\s()-]+") || digits.length() < 10 || digits.length() > 15) {
            throw new InvalidRequestException("The WhatsApp number should look like 01712-345678 or +8801712345678.");
        }
        return v;
    }

    private static String text(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        if (v.length() > max) throw new InvalidRequestException("That text is too long (at most " + max + " letters).");
        return v;
    }

    private static String blank(String v) {
        return v == null ? "" : v;
    }
}
