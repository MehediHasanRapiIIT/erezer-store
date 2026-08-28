package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.ordernote.OrderNoteDTO;
import kn.org.deliverybackend.dto.ordernote.OrderNoteRequestDTO;

import java.util.List;
import java.util.UUID;

public interface OrderNoteService {

    List<OrderNoteDTO> list(UUID orderId);

    OrderNoteDTO add(UUID orderId, OrderNoteRequestDTO request, String author);

    void delete(UUID orderId, UUID noteId);
}
