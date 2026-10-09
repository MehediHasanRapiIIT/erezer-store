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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Replying to a support message from the admin panel: the customer is emailed,
 * the reply is kept under the message, and nothing is kept when the email could
 * not be sent.
 */
class ContactReplyTest {

    private static final UUID MESSAGE = UUID.randomUUID(), STAFF = UUID.randomUUID();

    private final ContactMessageRepository messages = mock(ContactMessageRepository.class);
    private final ContactReplyRepository replies = mock(ContactReplyRepository.class);
    private final StoreSettingsRepository settingsRepository = mock(StoreSettingsRepository.class);
    private final EmailService email = mock(EmailService.class);
    private final ContactReplyService service = new ContactReplyService(messages, replies, settingsRepository, email);

    private final ContactMessage message = ContactMessage.builder()
            .name("Rahim").email("rahim@example.com").subject("Where is my order?")
            .message("I ordered last week.\n\nIt has not arrived.").status("NEW").build();
    private final StoreSettings settings = new StoreSettings();
    private final List<ContactReply> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        message.setId(MESSAGE);
        message.setDeleted(false);
        settings.setSupportEmail("care@erezer.com");
        when(messages.findById(MESSAGE)).thenReturn(Optional.of(message));
        when(messages.save(any(ContactMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settingsRepository.findById(any())).thenReturn(Optional.of(settings));
        when(replies.save(any(ContactReply.class))).thenAnswer(inv -> {
            ContactReply r = inv.getArgument(0);
            r.setId((long) stored.size() + 1);
            stored.add(r);
            return r;
        });
        when(replies.findForMessages(any())).thenAnswer(inv -> new ArrayList<>(stored));
        when(email.sendNow(anyString(), anyString(), anyString(), any(), any())).thenReturn(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aReplyIsEmailedToTheCustomerKeptAndMarksTheMessageAnswered() {
        ContactReplyDTO sent = service.reply(MESSAGE, "  It is on its way.\r\n\r\nIt arrives tomorrow.  ", STAFF, "Ayesha");

        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(email).sendNow(eq("rahim@example.com"), eq("Re: Where is my order?"), eq("support-reply"), variables.capture(), eq("care@erezer.com"));
        assertEquals("Rahim", variables.getValue().get("customerName"));
        assertEquals(List.of("It is on its way.", "It arrives tomorrow."), variables.getValue().get("replyParagraphs"));
        assertEquals(List.of("I ordered last week.", "It has not arrived."), variables.getValue().get("originalParagraphs"),
                "the customer's own message is quoted under the reply");

        assertEquals("It is on its way.\n\nIt arrives tomorrow.", sent.getBody());
        assertEquals("Ayesha", sent.getSentByName());
        assertEquals("rahim@example.com", sent.getSentTo());
        assertEquals(1, stored.size());
        assertEquals(STAFF, stored.get(0).getSentById());
        assertEquals("RESOLVED", message.getStatus());
    }

    @Test
    void aReplyThatCouldNotBeSentIsNotKeptAndTheMessageStaysUnanswered() {
        when(email.sendNow(anyString(), anyString(), anyString(), any(), any())).thenReturn(false);

        InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> service.reply(MESSAGE, "Hello", STAFF, "Ayesha"));
        assertTrue(e.getMessage().contains("could not be sent"), e.getMessage());
        assertTrue(stored.isEmpty());
        assertEquals("NEW", message.getStatus());
    }

    @Test
    void anEmptyOrOverlongReplyIsRefusedBeforeAnythingIsSent() {
        assertThrows(InvalidRequestException.class, () -> service.reply(MESSAGE, "   ", STAFF, "Ayesha"));
        assertThrows(InvalidRequestException.class, () -> service.reply(MESSAGE, "x".repeat(4001), STAFF, "Ayesha"));
        verify(email, never()).sendNow(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void aMessageWithNoSubjectOrThatIsDeletedIsHandled() {
        message.setSubject(null);
        service.reply(MESSAGE, "Hello", STAFF, null);
        verify(email).sendNow(eq("rahim@example.com"), eq("Re: Your message to Erezer"), anyString(), any(), any());
        assertEquals("Staff", stored.get(0).getSentByName());

        message.setDeleted(true);
        assertThrows(ResourceNotFoundException.class, () -> service.reply(MESSAGE, "Hello", STAFF, "Ayesha"));
    }

    @Test
    void aSubjectThatAlreadySaysReIsNotGivenAnotherAndNoSupportAddressMeansNoReplyTo() {
        message.setSubject("RE: refund");
        settings.setSupportEmail(" ");
        service.reply(MESSAGE, "Hello", STAFF, "Ayesha");
        verify(email).sendNow(eq("rahim@example.com"), eq("RE: refund"), anyString(), any(), isNull());
    }

    @Test
    void everyReplyIsListedUnderItsMessageOldestFirst() {
        service.reply(MESSAGE, "First answer.", STAFF, "Ayesha");
        service.reply(MESSAGE, "Second answer.", STAFF, "Karim");
        stored.forEach(r -> r.setMessageId(MESSAGE));

        ContactMessageDTO dto = ContactMessageDTO.builder().id(MESSAGE).build();
        ContactMessageDTO other = ContactMessageDTO.builder().id(UUID.randomUUID()).build();
        service.attachReplies(List.of(dto, other));

        assertEquals(List.of("First answer.", "Second answer."), dto.getReplies().stream().map(ContactReplyDTO::getBody).toList());
        assertEquals(List.of("Ayesha", "Karim"), dto.getReplies().stream().map(ContactReplyDTO::getSentByName).toList());
        assertTrue(other.getReplies().isEmpty());
    }
}
