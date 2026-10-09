package kn.org.deliverybackend.service;

import java.util.List;
import java.util.Map;

public interface EmailService {

    /**
     * Renders the named Thymeleaf template under templates/email/ with the
     * provided variables and sends it. Execution is async — callers should not
     * block the request thread on email delivery.
     */
    void send(String toEmail, String subject, String templateName, Map<String, Object> variables);

    /**
     * Same as {@link #send}, but with binary attachments (e.g. flattened design
     * preview PNGs). Also async.
     */
    /**
     * Sends at once, on the caller's thread, and says whether the mail server
     * took it. For an email a person is waiting to hear the fate of - a reply
     * typed in the admin panel - where failing quietly would be worse than
     * making them wait a second. {@code replyTo} may be null.
     */
    boolean sendNow(String toEmail, String subject, String templateName, Map<String, Object> variables, String replyTo);

    void sendWithAttachments(String toEmail, String subject, String templateName,
                             Map<String, Object> variables, List<EmailAttachment> attachments);
}
