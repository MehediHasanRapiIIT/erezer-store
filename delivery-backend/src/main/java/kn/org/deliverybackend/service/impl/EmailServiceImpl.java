package kn.org.deliverybackend.service.impl;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Override
    @Async("emailExecutor")
    public void send(String toEmail, String subject, String templateName, Map<String, Object> variables) {
        try {
            Context ctx = new Context();
            if (variables != null) {
                variables.forEach(ctx::setVariable);
            }
            String html = templateEngine.process("email/" + templateName, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, fromName));
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
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
            Context ctx = new Context();
            if (variables != null) {
                variables.forEach(ctx::setVariable);
            }
            String html = templateEngine.process("email/" + templateName, ctx);

            MimeMessage message = mailSender.createMimeMessage();
            // multipart=true is required so attachments can be added alongside the HTML body.
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, fromName));
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);

            if (attachments != null) {
                for (EmailAttachment att : attachments) {
                    if (att == null || att.content() == null || att.content().length == 0) {
                        continue;
                    }
                    helper.addAttachment(att.filename(), new ByteArrayResource(att.content()), att.contentType());
                }
            }

            mailSender.send(message);
            log.info("Email sent with {} attachment(s): to={} template={} subject={}",
                    attachments == null ? 0 : attachments.size(), toEmail, templateName, subject);
        } catch (Exception ex) {
            log.error("Failed to send email with attachments to {} (template {}): {}",
                    toEmail, templateName, ex.getMessage(), ex);
        }
    }
}
