package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.contact.ContactMessageDTO;
import kn.org.deliverybackend.dto.contact.ContactReplyDTO;
import kn.org.deliverybackend.entity.ContactMessage;
import kn.org.deliverybackend.entity.ContactReply;
import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.ContactMessageRepository;
import kn.org.deliverybackend.repository.ContactReplyRepository;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import kn.org.deliverybackend.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Answering a support message from the admin panel.
 *
 * <p>The reply goes to the customer by email, from the shop's own address, and
 * is kept under the message with who sent it and when. It is only kept if the
 * email was handed to the mail server: a reply that could not be sent is
 * refused, so the list never says "answered" about a customer who heard nothing.
 */
@Service
@RequiredArgsConstructor
public class ContactReplyService {

    private final ContactMessageRepository messageRepository;
    private final ContactReplyRepository replyRepository;
    private final StoreSettingsRepository settingsRepository;
    private final EmailService emailService;

    /**
     * Sends {@code body} to the customer who wrote the message and records it.
     * The message becomes RESOLVED: it has been answered.
     */
    @Transactional
    public ContactReplyDTO reply(UUID messageId, String body, UUID staffId, String staffName) {
        ContactMessage message = messageRepository.findById(messageId)
                .filter(m -> !Boolean.TRUE.equals(m.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));
        String text = body == null ? "" : body.replace("\r\n", "\n").trim();
        if (text.isEmpty()) throw new InvalidRequestException("Write your reply first.");
        if (text.length() > 4000) throw new InvalidRequestException("A reply can be up to 4000 letters.");

        String subject = message.getSubject() == null || message.getSubject().isBlank()
                ? "Your message to Erezer" : message.getSubject().trim();
        Map<String, Object> variables = new HashMap<>();
        variables.put("customerName", message.getName());
        variables.put("replyParagraphs", paragraphs(text));
        variables.put("originalParagraphs", paragraphs(message.getMessage()));
        variables.put("originalSubject", subject);

        // A customer who answers the email should reach people, not the no-reply address it is sent from.
        String replyTo = settingsRepository.findById(StoreSettings.SINGLETON_ID)
                .map(StoreSettings::getSupportEmail).filter(e -> e != null && !e.isBlank()).orElse(null);

        boolean sent = emailService.sendNow(message.getEmail(), subject.regionMatches(true, 0, "Re:", 0, 3) ? subject : "Re: " + subject,
                "support-reply", variables, replyTo);
        if (!sent) {
            throw new InvalidRequestException(
                    "The email could not be sent, so the reply was not saved. Check the shop's email settings, or try again in a moment.");
        }

        ContactReply reply = new ContactReply();
        reply.setMessageId(messageId);
        reply.setBody(text);
        reply.setSentById(staffId);
        reply.setSentByName(staffName == null || staffName.isBlank() ? "Staff" : staffName.trim());
        reply.setSentTo(message.getEmail());
        ContactReply saved = replyRepository.save(reply);

        message.setStatus("RESOLVED");
        messageRepository.save(message);
        return toDTO(saved, LocalDateTime.now());
    }

    /** Puts each message's replies on it, oldest first, reading them for the whole list at once. */
    @Transactional(readOnly = true)
    public void attachReplies(List<ContactMessageDTO> messages) {
        if (messages == null || messages.isEmpty()) return;
        Map<UUID, List<ContactReplyDTO>> byMessage = new HashMap<>();
        for (ContactReply reply : replyRepository.findForMessages(messages.stream().map(ContactMessageDTO::getId).toList())) {
            LocalDateTime sentAt = reply.getCreatedAt() == null ? null
                    : LocalDateTime.ofInstant(reply.getCreatedAt().toInstant(), ZoneId.systemDefault());
            byMessage.computeIfAbsent(reply.getMessageId(), k -> new ArrayList<>()).add(toDTO(reply, sentAt));
        }
        for (ContactMessageDTO message : messages) {
            message.setReplies(byMessage.getOrDefault(message.getId(), List.of()));
        }
    }

    private static ContactReplyDTO toDTO(ContactReply reply, LocalDateTime sentAt) {
        return ContactReplyDTO.builder()
                .id(reply.getId())
                .body(reply.getBody())
                .sentByName(reply.getSentByName())
                .sentAt(sentAt)
                .sentTo(reply.getSentTo())
                .build();
    }

    /** The text as paragraphs, so the email keeps the breaks the writer made. */
    static List<String> paragraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        for (String part : (text == null ? "" : text).replace("\r\n", "\n").split("\n\\s*\n")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) paragraphs.add(trimmed);
        }
        return paragraphs;
    }
}
