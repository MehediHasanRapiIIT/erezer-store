package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.order.OrderStatusUpdateRequestDTO;
import kn.org.deliverybackend.dto.returns.ReturnDecisionDTO;
import kn.org.deliverybackend.dto.returns.ReturnItemDTO;
import kn.org.deliverybackend.dto.returns.ReturnItemRequestDTO;
import kn.org.deliverybackend.dto.returns.ReturnPhotoDTO;
import kn.org.deliverybackend.dto.returns.ReturnRequestCreateDTO;
import kn.org.deliverybackend.dto.returns.ReturnRequestDTO;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.OrderItem;
import kn.org.deliverybackend.entity.ReturnItem;
import kn.org.deliverybackend.entity.ReturnPhoto;
import kn.org.deliverybackend.entity.ReturnRequest;
import kn.org.deliverybackend.enumeration.OrderStatus;
import kn.org.deliverybackend.enumeration.ReturnReason;
import kn.org.deliverybackend.enumeration.ReturnStatus;
import kn.org.deliverybackend.event.ReturnDecisionEvent;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.OrderItemRepository;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.ReturnItemRepository;
import kn.org.deliverybackend.repository.ReturnPhotoRepository;
import kn.org.deliverybackend.repository.ReturnRequestRepository;
import kn.org.deliverybackend.service.FileStorageService;
import kn.org.deliverybackend.service.OrderHistoryService;
import kn.org.deliverybackend.service.ReturnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReturnServiceImpl implements ReturnService {

    private final ReturnRequestRepository returnRepository;
    private final ReturnItemRepository    returnItemRepository;
    private final ReturnPhotoRepository   returnPhotoRepository;
    private final OrderRepository         orderRepository;
    private final OrderItemRepository     orderItemRepository;
    private final ProductRepository       productRepository;
    private final OrderHistoryService     orderHistoryService;
    private final FileStorageService      fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.returns.window-days:14}")
    private long returnWindowDays;

    @Value("${app.returns.bucket:return-photos}")
    private String returnPhotosBucket;

    // ── Customer ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ReturnRequestDTO requestReturn(UUID userId,
                                          UUID orderId,
                                          ReturnRequestCreateDTO request,
                                          List<MultipartFile> photos) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (order.getClientId() == null || !order.getClientId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        enforceReturnable(order);

        ReturnReason reason = ReturnReason.parse(request.getReason())
                .orElseThrow(() -> new InvalidStockOperationException(
                        "Unknown return reason: " + request.getReason()));

        // Persist ReturnRequest
        ReturnRequest rr = ReturnRequest.builder()
                .orderId(orderId)
                .userId(userId)
                .customerEmail(order.getCustomerEmail())
                .status(ReturnStatus.REQUESTED.name())
                .reason(reason.name())
                .customerNotes(request.getCustomerNotes())
                .requestedAt(LocalDateTime.now())
                .build();
        ReturnRequest saved = returnRepository.save(rr);

        // Validate each returned item against the order's items + persist
        for (ReturnItemRequestDTO ri : request.getItems()) {
            OrderItem oi = orderItemRepository.findById(ri.getOrderItemId())
                    .orElseThrow(() -> new InvalidStockOperationException(
                            "Order item not found: " + ri.getOrderItemId()));
            if (!orderId.equals(oi.getOrderId())) {
                throw new InvalidStockOperationException(
                        "Order item " + ri.getOrderItemId() + " does not belong to order " + orderId);
            }
            if (ri.getQuantity() > oi.getQuantity()) {
                throw new InvalidStockOperationException(
                        "Cannot return more units than were ordered for item " + ri.getOrderItemId());
            }
            returnItemRepository.save(ReturnItem.builder()
                    .returnRequestId(saved.getId())
                    .orderItemId(oi.getId())
                    .productId(oi.getProductId())
                    .quantity(ri.getQuantity())
                    .condition(ri.getCondition())
                    .build());
        }

        // Upload up to 3 photos
        if (photos != null) {
            int kept = 0;
            for (MultipartFile photo : photos) {
                if (kept >= 3) break;
                if (photo == null || photo.isEmpty()) continue;
                String url = fileStorageService.uploadFile(photo, returnPhotosBucket);
                returnPhotoRepository.save(ReturnPhoto.builder()
                        .returnRequestId(saved.getId())
                        .url(url)
                        .build());
                kept++;
            }
        }

        // Notify the customer that we received their request.
        eventPublisher.publishEvent(new ReturnDecisionEvent(
                this, saved.getId(), orderId, order.getCustomerEmail(),
                ReturnStatus.REQUESTED, null, null));

        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReturnRequestDTO> listMine(UUID userId) {
        return returnRepository.findByUser(userId).stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnRequestDTO getMine(UUID userId, UUID returnId) {
        ReturnRequest r = returnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found: " + returnId));
        if (r.getUserId() == null || !r.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Return not found: " + returnId);
        }
        return toDTO(r);
    }

    // ── Admin ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnRequestDTO> listForAdmin(String status, int page, int size) {
        String normalized = (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL"))
                ? status.toUpperCase() : null;
        return returnRepository.findForAdmin(normalized, PageRequest.of(page, size))
                .map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnRequestDTO getForAdmin(UUID returnId) {
        ReturnRequest r = returnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found: " + returnId));
        return toDTO(r);
    }

    @Override
    @Transactional
    public ReturnRequestDTO approve(UUID returnId, ReturnDecisionDTO decision, String adminIdentity) {
        ReturnRequest r = transitionInternal(returnId, ReturnStatus.APPROVED, decision, adminIdentity);
        eventPublisher.publishEvent(new ReturnDecisionEvent(
                this, r.getId(), r.getOrderId(), r.getCustomerEmail(),
                ReturnStatus.APPROVED, r.getAdminNotes(), r.getRefundAmount()));
        return toDTO(r);
    }

    @Override
    @Transactional
    public ReturnRequestDTO reject(UUID returnId, ReturnDecisionDTO decision, String adminIdentity) {
        ReturnRequest r = transitionInternal(returnId, ReturnStatus.REJECTED, decision, adminIdentity);
        eventPublisher.publishEvent(new ReturnDecisionEvent(
                this, r.getId(), r.getOrderId(), r.getCustomerEmail(),
                ReturnStatus.REJECTED, r.getAdminNotes(), null));
        return toDTO(r);
    }

    @Override
    @Transactional
    public ReturnRequestDTO markPickedUp(UUID returnId, String adminIdentity) {
        ReturnRequest r = transitionInternal(returnId, ReturnStatus.PICKED_UP, null, adminIdentity);
        r.setPickedUpAt(LocalDateTime.now());
        returnRepository.save(r);
        eventPublisher.publishEvent(new ReturnDecisionEvent(
                this, r.getId(), r.getOrderId(), r.getCustomerEmail(),
                ReturnStatus.PICKED_UP, r.getAdminNotes(), r.getRefundAmount()));
        return toDTO(r);
    }

    @Override
    @Transactional
    public ReturnRequestDTO markRefunded(UUID returnId, ReturnDecisionDTO decision, String adminIdentity) {
        ReturnRequest r = transitionInternal(returnId, ReturnStatus.REFUNDED, decision, adminIdentity);
        r.setRefundedAt(LocalDateTime.now());
        returnRepository.save(r);

        // Move the parent order to RETURNED (state-machine validated).
        try {
            OrderStatusUpdateRequestDTO orderUpdate = new OrderStatusUpdateRequestDTO();
            orderUpdate.setStatus(OrderStatus.RETURNED.name());
            orderUpdate.setNote("Return " + r.getId() + " refunded");
            orderHistoryService.updateOrderStatus(r.getOrderId(), orderUpdate,
                    adminIdentity != null ? adminIdentity : "admin:returns");
        } catch (Exception ex) {
            // Some statuses (CANCELLED) can't transition to RETURNED; log and continue.
            log.warn("Could not transition order {} to RETURNED after refund: {}",
                    r.getOrderId(), ex.getMessage());
        }

        eventPublisher.publishEvent(new ReturnDecisionEvent(
                this, r.getId(), r.getOrderId(), r.getCustomerEmail(),
                ReturnStatus.REFUNDED, r.getAdminNotes(), r.getRefundAmount()));
        return toDTO(r);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void enforceReturnable(Order order) {
        OrderStatus current = OrderStatus.parse(order.getOrderStatus())
                .map(OrderStatus::normalize)
                .orElse(OrderStatus.PLACED);
        if (current != OrderStatus.DELIVERED) {
            throw new InvalidStockOperationException(
                    "Returns are only allowed on delivered orders (current: " + current + ").");
        }
        LocalDateTime delivered = order.getDeliveredAt();
        if (delivered == null) {
            // Fall back to createdAt if delivery wasn't snapshot — defensive only.
            if (order.getCreatedAt() != null) {
                delivered = LocalDateTime.ofInstant(order.getCreatedAt().toInstant(),
                        java.time.ZoneId.systemDefault());
            }
        }
        if (delivered != null
                && delivered.plusDays(returnWindowDays).isBefore(LocalDateTime.now())) {
            throw new InvalidStockOperationException(
                    "Return window of " + returnWindowDays + " days has closed.");
        }
    }

    private ReturnRequest transitionInternal(UUID returnId,
                                              ReturnStatus target,
                                              ReturnDecisionDTO decision,
                                              String adminIdentity) {
        ReturnRequest r = returnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found: " + returnId));
        ReturnStatus current = ReturnStatus.parse(r.getStatus())
                .orElseThrow(() -> new InvalidStockOperationException(
                        "Stored return has bad status: " + r.getStatus()));
        if (!current.canTransitionTo(target)) {
            throw new InvalidStockOperationException(
                    "Illegal return transition: " + current + " → " + target +
                            " (allowed: " + current.nextStates() + ")");
        }
        r.setStatus(target.name());
        r.setDecidedAt(LocalDateTime.now());
        r.setDecidedBy(adminIdentity != null ? adminIdentity : "admin");
        if (decision != null) {
            if (decision.getAdminNotes() != null && !decision.getAdminNotes().isBlank()) {
                r.setAdminNotes(decision.getAdminNotes());
            }
            if (decision.getRefundAmount() != null) {
                r.setRefundAmount(decision.getRefundAmount());
            }
        }
        return returnRepository.save(r);
    }

    private ReturnRequestDTO toDTO(ReturnRequest r) {
        List<ReturnItemDTO> items = returnItemRepository.findByReturnRequestId(r.getId()).stream()
                .map(this::toItemDTO).toList();
        List<ReturnPhotoDTO> photos = returnPhotoRepository.findByReturnRequestIdOrderByIdAsc(r.getId())
                .stream().map(this::toPhotoDTO).toList();
        return ReturnRequestDTO.builder()
                .id(r.getId())
                .orderId(r.getOrderId())
                .userId(r.getUserId())
                .customerEmail(r.getCustomerEmail())
                .status(r.getStatus())
                .reason(r.getReason())
                .customerNotes(r.getCustomerNotes())
                .adminNotes(r.getAdminNotes())
                .refundAmount(r.getRefundAmount())
                .requestedAt(r.getRequestedAt())
                .decidedAt(r.getDecidedAt())
                .decidedBy(r.getDecidedBy())
                .pickedUpAt(r.getPickedUpAt())
                .refundedAt(r.getRefundedAt())
                .items(items)
                .photos(photos)
                .build();
    }

    private ReturnItemDTO toItemDTO(ReturnItem i) {
        String name = i.getProductId() == null ? null
                : productRepository.findById(i.getProductId()).map(p -> p.getName()).orElse(null);
        return ReturnItemDTO.builder()
                .id(i.getId())
                .orderItemId(i.getOrderItemId())
                .productId(i.getProductId())
                .productName(name)
                .quantity(i.getQuantity())
                .condition(i.getCondition())
                .lineRefundAmount(i.getLineRefundAmount())
                .build();
    }

    private ReturnPhotoDTO toPhotoDTO(ReturnPhoto p) {
        return ReturnPhotoDTO.builder()
                .id(p.getId())
                .url(p.getUrl())
                .caption(p.getCaption())
                .build();
    }
}
