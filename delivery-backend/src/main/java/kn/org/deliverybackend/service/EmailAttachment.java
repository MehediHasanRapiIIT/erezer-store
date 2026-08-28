package kn.org.deliverybackend.service;

/**
 * An in-memory file attachment for {@link EmailService#sendWithAttachments}.
 *
 * @param filename    the name the recipient sees (e.g. {@code front.png})
 * @param contentType MIME type (e.g. {@code image/png})
 * @param content     the raw bytes
 */
public record EmailAttachment(String filename, String contentType, byte[] content) {
}
