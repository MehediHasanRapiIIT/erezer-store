package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.contact.ContactMessageDTO;
import kn.org.deliverybackend.dto.contact.ContactMessageRequestDTO;
import kn.org.deliverybackend.dto.contact.ContactStatusUpdateDTO;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ContactMessageService {

    ContactMessageDTO submit(ContactMessageRequestDTO request);

    Page<ContactMessageDTO> list(String status, int page, int size);

    ContactMessageDTO get(UUID id);

    ContactMessageDTO updateStatus(UUID id, ContactStatusUpdateDTO update);

    void delete(UUID id);
}
