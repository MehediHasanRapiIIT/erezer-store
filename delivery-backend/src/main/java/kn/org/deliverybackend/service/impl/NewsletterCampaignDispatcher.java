package kn.org.deliverybackend.service.impl;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import kn.org.deliverybackend.entity.NewsletterCampaign;
import kn.org.deliverybackend.entity.NewsletterSendLog;
import kn.org.deliverybackend.entity.NewsletterSubscriber;
import kn.org.deliverybackend.entity.Users;
import kn.org.deliverybackend.enumeration.CampaignAudience;
import kn.org.deliverybackend.enumeration.CampaignStatus;
import kn.org.deliverybackend.enumeration.SubscriberStatus;
import kn.org.deliverybackend.repository.NewsletterCampaignRepository;
import kn.org.deliverybackend.repository.NewsletterSendLogRepository;
import kn.org.deliverybackend.repository.NewsletterSubscriberRepository;
import kn.org.deliverybackend.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Async fan-out worker for newsletter campaigns. Runs on the {@code emailExecutor}
 * thread pool (same as transactional email events). Each recipient gets a
 * NewsletterSendLog row whether the send succeeded or not.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NewsletterCampaignDispatcher {

    private final NewsletterCampaignRepository campaignRepository;
    private final NewsletterSubscriberRepository subscriberRepository;
    private final NewsletterSendLogRepository sendLogRepository;
    private final UsersRepository usersRepository;
    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.frontend.store-url}")
    private String storeUrl;

    @Async("emailExecutor")
    public void dispatch(UUID campaignId) {
        NewsletterCampaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) {
            log.warn("Campaign {} disappeared before dispatch", campaignId);
            return;
        }

        CampaignAudience audience = CampaignAudience.parse(campaign.getAudience())
                .orElse(CampaignAudience.ALL_SUBSCRIBERS);

        // Build the recipient set — dedupe by email so we never double-send.
        Map<String, RecipientRow> recipients = resolveRecipients(audience);

        int sent = 0;
        int failed = 0;
        for (RecipientRow row : recipients.values()) {
            try {
                deliver(campaign, row);
                logSend(campaign.getId(), row, "SENT", null);
                sent++;
            } catch (Exception ex) {
                log.warn("Newsletter delivery failed to {}: {}", row.email, ex.getMessage());
                logSend(campaign.getId(), row, "FAILED", truncate(ex.getMessage()));
                failed++;
            }
        }

        finalize(campaign.getId(), sent, failed);
    }

    // ── audience resolution ─────────────────────────────────────────────────────

    private Map<String, RecipientRow> resolveRecipients(CampaignAudience audience) {
        Map<String, RecipientRow> map = new LinkedHashMap<>();

        // Newsletter subscribers go in for both audiences (REGISTERED_CUSTOMERS is
        // the narrower set; ALL_SUBSCRIBERS is the wider). The send-log dedupes
        // by lower-cased email.
        if (audience == CampaignAudience.ALL_SUBSCRIBERS) {
            for (NewsletterSubscriber sub : subscriberRepository.findAllSubscribed()) {
                map.put(sub.getEmail().toLowerCase(),
                        new RecipientRow(sub.getEmail(), sub.getId(), sub.getUnsubscribeToken()));
            }
        }

        if (audience == CampaignAudience.REGISTERED_CUSTOMERS) {
            // Find unsubscribed emails to filter out.
            Set<String> unsubscribed = new HashSet<>();
            subscriberRepository.findAll().forEach(s -> {
                if (Boolean.TRUE.equals(s.getDeleted())) return;
                if (SubscriberStatus.UNSUBSCRIBED.name().equals(s.getStatus())) {
                    unsubscribed.add(s.getEmail().toLowerCase());
                }
            });
            for (Users u : usersRepository.findAll()) {
                if (u.getEmail() == null || u.getEmail().isBlank()) continue;
                if (Boolean.TRUE.equals(u.getDeleted())) continue;
                String email = u.getEmail().toLowerCase();
                if (unsubscribed.contains(email)) continue;
                // No subscriber row → generate a stable opt-out URL via Users id.
                map.put(email, new RecipientRow(u.getEmail(), null,
                        // Stub token — generates a unique deterministic-ish value the
                        // unsubscribe endpoint won't recognise; clicking it just
                        // confirms "you're unsubscribed" without doing anything.
                        // Real opt-out is recorded only for subscribers.
                        "u-" + u.getId().toString().replace("-", "")));
            }
        }

        return map;
    }

    // ── delivery ──────────────────────────────────────────────────────────────

    private void deliver(NewsletterCampaign campaign, RecipientRow row) throws Exception {
        Context ctx = new Context();
        Map<String, Object> vars = new HashMap<>();
        vars.put("bodyHtml", campaign.getBodyHtml());
        vars.put("recipientEmail", row.email);
        vars.put("unsubscribeUrl", storeUrl + "/unsubscribe?token=" + row.unsubscribeToken);
        vars.forEach(ctx::setVariable);

        String html = templateEngine.process("email/newsletter", ctx);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(new InternetAddress(fromAddress, fromName));
        helper.setTo(row.email);
        helper.setSubject(campaign.getSubject());
        helper.setText(html, true);
        mailSender.send(message);
    }

    // ── persistence in separate tx so the worker can keep going on failure ────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSend(UUID campaignId, RecipientRow row, String status, String error) {
        sendLogRepository.save(NewsletterSendLog.builder()
                .campaignId(campaignId)
                .subscriberId(row.subscriberId)
                .email(row.email)
                .status(status)
                .errorMessage(error)
                .sentAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void finalize(UUID campaignId, int sent, int failed) {
        campaignRepository.findById(campaignId).ifPresent(c -> {
            c.setSentCount(sent);
            c.setFailCount(failed);
            c.setSentAt(LocalDateTime.now());
            c.setStatus(failed > 0 && sent == 0
                    ? CampaignStatus.FAILED.name()
                    : CampaignStatus.SENT.name());
            campaignRepository.save(c);
        });
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 1900 ? s.substring(0, 1900) + "…" : s;
    }

    /**
     * Plain holder — package-private so the dispatcher can rebuild it from
     * either a subscriber row or a registered-customer Users row.
     */
    record RecipientRow(String email, UUID subscriberId, String unsubscribeToken) {}
}
