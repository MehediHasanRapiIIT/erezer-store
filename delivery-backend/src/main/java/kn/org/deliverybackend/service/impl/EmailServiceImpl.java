package kn.org.deliverybackend.service.impl;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import kn.org.deliverybackend.service.EmailAttachment;
import kn.org.deliverybackend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.List;
import java.util.Map;

/**
 * Sends the shop's emails. Each one goes out the way mail services expect a
 * real business's email to look: an HTML version and a plain-text version of
 * the same words, the shop's logo, address and contacts in the footer, and
 * replies going to the support address rather than to the no-reply sender.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final StoreSettingsRepository storeSettings;
    private final CustomerEmailSettingsService customerEmails;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.frontend.store-url}")
    private String storeUrl;

    @Override
    public boolean sendNow(String toEmail, String subject, String templateName, Map<String, Object> variables, String replyTo) {
        try {
            mailSender.send(compose(toEmail, subject, templateName, variables, replyTo, null));
            log.info("Email sent: to={} template={} subject={}", toEmail, templateName, subject);
            return true;
        } catch (Exception ex) {
            log.error("Failed to send email to {} (template {}): {}", toEmail, templateName, ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    @Async("emailExecutor")
    public void send(String toEmail, String subject, String templateName, Map<String, Object> variables) {
        try {
            mailSender.send(compose(toEmail, subject, templateName, variables, null, null));
            log.info("Email sent: to={} template={} subject={}", toEmail, templateName, subject);
        } catch (Exception ex) {
            log.error("Failed to send email to {} (template {}): {}", toEmail, templateName, ex.getMessage(), ex);
        }
    }

    @Override
    @Async("emailExecutor")
    public void sendWithAttachments(String toEmail, String subject, String templateName,
                                    Map<String, Object> variables, List<EmailAttachment> attachments) {
        try {
            mailSender.send(compose(toEmail, subject, templateName, variables, null, attachments));
            log.info("Email sent with {} attachment(s): to={} template={} subject={}",
                    attachments == null ? 0 : attachments.size(), toEmail, templateName, subject);
        } catch (Exception ex) {
            log.error("Failed to send email with attachments to {} (template {}): {}",
                    toEmail, templateName, ex.getMessage(), ex);
        }
    }

    /** One email: the template in HTML and plain text, the shop's footer, and where replies go. */
    private MimeMessage compose(String toEmail, String subject, String templateName, Map<String, Object> variables,
                                String replyTo, List<EmailAttachment> attachments) throws Exception {
        StoreSettings shop = storeSettings.findById(StoreSettings.SINGLETON_ID).orElse(null);
        String supportEmail = shop == null ? null : blankToNull(shop.getSupportEmail());

        Context ctx = new Context();
        ctx.setVariable("subject", subject);
        ctx.setVariable("brandStoreUrl", storeUrl);
        ctx.setVariable("brandSite", siteName(storeUrl));
        ctx.setVariable("brandLogoUrl", storeUrl + "/logo-wordmark.png");
        ctx.setVariable("brandSupportEmail", supportEmail);
        ctx.setVariable("brandSupportPhone", shop == null ? null : blankToNull(shop.getSupportPhone()));
        ctx.setVariable("brandAddress", blankToNull(safeAddress()));
        ctx.setVariable("brandYear", Year.now().getValue());
        if (variables != null) {
            variables.forEach(ctx::setVariable);
        }
        String html = templateEngine.process("email/" + templateName, ctx);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(new InternetAddress(fromAddress, fromName));
        String answerTo = replyTo != null && !replyTo.isBlank() ? replyTo.trim() : supportEmail;
        if (answerTo != null && !answerTo.equalsIgnoreCase(fromAddress)) {
            helper.setReplyTo(answerTo);
        }
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(EmailText.plain(html), html);
        // An order email is sent by the shop's system, not typed by a person:
        // saying so stops auto-replies (out-of-office) bouncing back to us.
        message.setHeader("Auto-Submitted", "auto-generated");

        if (attachments != null) {
            for (EmailAttachment att : attachments) {
                if (att == null || att.content() == null || att.content().length == 0) {
                    continue;
                }
                helper.addAttachment(att.filename(), new ByteArrayResource(att.content()), att.contentType());
            }
        }
        return message;
    }

    private String safeAddress() {
        try {
            return customerEmails.get().businessAddress();
        } catch (Exception e) {
            return null;
        }
    }

    /** https://erezer.com → erezer.com, for the footer. */
    static String siteName(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? url : host.replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return url;
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
