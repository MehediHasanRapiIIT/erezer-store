package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.contact.ContactMessageDTO;
import kn.org.deliverybackend.dto.contact.ContactMessageRequestDTO;
import kn.org.deliverybackend.dto.contact.ContactStatusUpdateDTO;
import kn.org.deliverybackend.entity.ContactMessage;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.ContactMessageRepository;
import kn.org.deliverybackend.service.ContactMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContactMessageServiceImpl implements ContactMessageService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("NEW", "READ", "RESOLVED");

    private final ContactMessageRepository repository;

    @Override
    @Transactional
    public ContactMessageDTO submit(ContactMessageRequestDTO request) {
        ContactMessage m = ContactMessage.builder()
                .name(request.getName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .subject(request.getSubject() == null ? null : request.getSubject().trim())
                .message(request.getMessage().trim())
                .orderId(request.getOrderId())
                .status("NEW")
                .build();
        return toDTO(repository.save(m));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContactMessageDTO> list(String status, int page, int size) {
        String normalized = (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL"))
                ? status.toUpperCase() : null;
        return repository.findForAdmin(normalized, PageRequest.of(page, size)).map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public ContactMessageDTO get(UUID id) {
        ContactMessage m = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + id));
        return toDTO(m);
    }

    @Override
    @Transactional
    public ContactMessageDTO updateStatus(UUID id, ContactStatusUpdateDTO update) {
        String next = update.getStatus().toUpperCase();
        if (!ALLOWED_STATUSES.contains(next)) {
            throw new InvalidStockOperationException("Unknown contact status: " + update.getStatus());
        }
        ContactMessage m = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + id));
        m.setStatus(next);
        return toDTO(repository.save(m));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        ContactMessage m = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + id));
        m.setDeleted(true);
        repository.save(m);
    }

    private ContactMessageDTO toDTO(ContactMessage m) {
        LocalDateTime created = m.getCreatedAt() == null ? null
                : LocalDateTime.ofInstant(m.getCreatedAt().toInstant(),
                        java.time.ZoneId.systemDefault());
        return ContactMessageDTO.builder()
                .id(m.getId())
                .name(m.getName())
                .email(m.getEmail())
                .subject(m.getSubject())
                .message(m.getMessage())
                .status(m.getStatus())
                .orderId(m.getOrderId())
                .createdAt(created)
                .build();
    }
}
