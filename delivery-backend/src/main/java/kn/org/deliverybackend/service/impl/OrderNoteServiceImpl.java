package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.ordernote.OrderNoteDTO;
import kn.org.deliverybackend.dto.ordernote.OrderNoteRequestDTO;
import kn.org.deliverybackend.entity.OrderNote;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.OrderNoteRepository;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.service.OrderNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderNoteServiceImpl implements OrderNoteService {

    private final OrderNoteRepository noteRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public List<OrderNoteDTO> list(UUID orderId) {
        ensureOrderExists(orderId);
        return noteRepository.findByOrderId(orderId).stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional
    public OrderNoteDTO add(UUID orderId, OrderNoteRequestDTO request, String author) {
        ensureOrderExists(orderId);
        OrderNote n = OrderNote.builder()
                .orderId(orderId)
                .body(request.getBody().trim())
                .author(author != null ? author : "admin")
                .build();
        return toDTO(noteRepository.save(n));
    }

    @Override
    @Transactional
    public void delete(UUID orderId, UUID noteId) {
        ensureOrderExists(orderId);
        OrderNote n = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found: " + noteId));
        if (!orderId.equals(n.getOrderId())) {
            throw new ResourceNotFoundException("Note not found for this order: " + noteId);
        }
        n.setDeleted(true);
        noteRepository.save(n);
    }

    private void ensureOrderExists(UUID orderId) {
        orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private OrderNoteDTO toDTO(OrderNote n) {
        LocalDateTime created = n.getCreatedAt() == null ? null
                : LocalDateTime.ofInstant(n.getCreatedAt().toInstant(),
                        java.time.ZoneId.systemDefault());
        return OrderNoteDTO.builder()
                .id(n.getId())
                .orderId(n.getOrderId())
                .body(n.getBody())
                .author(n.getAuthor())
                .createdAt(created)
                .build();
    }
}
