package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.OrderDTO;
import kn.org.deliverybackend.dto.OrderItemDTO;
import kn.org.deliverybackend.dto.OrderSummaryDTO;
import kn.org.deliverybackend.dto.order.OrderStatusUpdateRequestDTO;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.OrderItem;
import kn.org.deliverybackend.entity.OrderStatusHistory;
import kn.org.deliverybackend.entity.Users;
import kn.org.deliverybackend.enumeration.OrderStatus;
import kn.org.deliverybackend.event.OrderStatusChangedEvent;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import kn.org.deliverybackend.mapper.OrderItemMapper;
import kn.org.deliverybackend.mapper.OrderMapper;
import kn.org.deliverybackend.repository.OrderItemRepository;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.OrderStatusHistoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.UserRiderRepository;
import kn.org.deliverybackend.repository.UsersRepository;
import kn.org.deliverybackend.service.OrderHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderHistoryServiceImpl implements OrderHistoryService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final UsersRepository usersRepository;
    private final UserRiderRepository userRiderRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<OrderDTO> getOrderHistory(UUID userId) {
        usersRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        return orderRepository.findByClientId(userId)
                .stream()
                .map(order -> {
                    OrderDTO dto = orderMapper.toDTO(order);
                    dto.setOrderItems(
                            orderItemRepository.findByOrderId(order.getId())
                                    .stream()
                                    .map(this::toEnrichedItemDTO)
                                    .collect(Collectors.toList())
                    );
                    usersRepository.findById(userId).ifPresent(user -> {
                        dto.setCustomerName(buildName(user));
                        dto.setCustomerPhone(user.getPhoneNumber());
                    });
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDTO getOrderDetails(UUID userId, UUID orderId) {
        usersRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        OrderDTO dto = orderRepository.findByIdAndClientId(orderId, userId)
                .map(orderMapper::toDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        dto.setOrderItems(
                orderItemRepository.findByOrderId(orderId)
                        .stream()
                        .map(this::toEnrichedItemDTO)
                        .collect(Collectors.toList())
        );

        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderDTO> getOrdersPaged(int page, int size, String status, String excludeStatus, String fromDate, String toDate) {
        PageRequest pageable = PageRequest.of(page, size);
        String statusParam = (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL"))
                ? status.toUpperCase() : null;
        // Active list hides finished orders (e.g. DELIVERED) which live in history.
        String excludeParam = (excludeStatus != null && !excludeStatus.isBlank())
                ? excludeStatus.toUpperCase() : null;

        // Pass ISO date strings directly; null means no filter
        String from = (fromDate != null && !fromDate.isBlank()) ? fromDate : null;
        // End of day for toDate
        String to = (toDate != null && !toDate.isBlank()) ? toDate + " 23:59:59" : null;

        return orderRepository.findOrdersFiltered(statusParam, excludeParam, from, to, pageable)
                .map(this::toOrderDTOWithItems);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDTO> getAllOrders() {
        return orderRepository.findAllOrders()
                .stream()
                .map(this::toOrderDTOWithItems)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDTO getOrderByIdForAdmin(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> !Boolean.TRUE.equals(o.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        return toOrderDTOWithItems(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDTO> getOrdersByStatus(String status) {
        return orderRepository.findByOrderStatus(status)
                .stream()
                .map(this::toOrderDTOWithItems)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrderDTO updateOrderStatus(UUID orderId, OrderStatusUpdateRequestDTO request, String changedBy) {
        if (request == null || request.getStatus() == null) {
            throw new InvalidStockOperationException("status is required");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        OrderStatus current = OrderStatus.parse(order.getOrderStatus())
                .map(OrderStatus::normalize)
                .orElse(OrderStatus.PLACED);
        OrderStatus target = OrderStatus.parse(request.getStatus())
                .orElseThrow(() -> new InvalidStockOperationException(
                        "Unknown order status: " + request.getStatus()));

        if (current == target) {
            // No-op transition — return current snapshot without history pollution.
            return toOrderDTOWithItems(order);
        }
        if (!current.canTransitionTo(target)) {
            throw new InvalidStockOperationException(
                    "Illegal transition: " + current + " → " + target +
                            " (allowed: " + current.nextStates() + ")");
        }

        order.setOrderStatus(target.name());

        // Shipping-specific fields when transitioning to SHIPPED
        if (target == OrderStatus.SHIPPED) {
            if (request.getCourierName() != null && !request.getCourierName().isBlank()) {
                order.setCourierName(request.getCourierName().trim());
            }
            if (request.getTrackingNumber() != null && !request.getTrackingNumber().isBlank()) {
                order.setTrackingNumber(request.getTrackingNumber().trim());
            }
        }
        if (target == OrderStatus.CANCELLED) {
            order.setCancellationReason(request.getNote());
            order.setCancelledAt(java.time.LocalDateTime.now());
        }
        if (target == OrderStatus.DELIVERED && order.getDeliveredAt() == null) {
            // Capture the delivery timestamp once so the return window has a
            // stable reference even if status moves further (e.g. RETURNED).
            order.setDeliveredAt(java.time.LocalDateTime.now());
        }

        Order saved = orderRepository.save(order);

        statusHistoryRepository.save(OrderStatusHistory.builder()
                .orderId(orderId)
                .fromStatus(current.name())
                .toStatus(target.name())
                .note(request.getNote())
                .changedBy(changedBy != null ? changedBy : "admin")
                .build());

        // Snapshot customer email — prefer the snapshot on the order row, fall
        // back to the Users row for legacy data.
        String email = saved.getCustomerEmail();
        if (email == null && saved.getClientId() != null) {
            email = usersRepository.findById(saved.getClientId())
                    .map(Users::getEmail).orElse(null);
        }

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                this, saved.getId(), email,
                current, target,
                request.getNote(),
                saved.getCourierName(),
                saved.getTrackingNumber()));

        return toOrderDTOWithItems(saved);
    }

    @Override
    @Deprecated
    public OrderDTO updateOrderStatus(UUID orderId, String status) {
        OrderStatusUpdateRequestDTO dto = new OrderStatusUpdateRequestDTO();
        dto.setStatus(status);
        return updateOrderStatus(orderId, dto, "admin:legacy");
    }

    @Override
    @Transactional(readOnly = true)
    public OrderSummaryDTO getSummary() {
        List<Order> all = orderRepository.findAllOrders();
        long total = all.size();
        long pending = all.stream().filter(o -> {
            OrderStatus s = OrderStatus.parse(o.getOrderStatus()).orElse(null);
            return s != null && (s.normalize() == OrderStatus.PLACED
                    || s == OrderStatus.ACCEPTED
                    || s == OrderStatus.IN_PRODUCTION
                    || s == OrderStatus.PROCESSING);
        }).count();
        long inTransit = all.stream().filter(o -> {
            OrderStatus s = OrderStatus.parse(o.getOrderStatus()).orElse(null);
            return s != null && (s == OrderStatus.SHIPPED || s == OrderStatus.OUT_FOR_DELIVERY);
        }).count();
        long completed = all.stream().filter(o -> {
            OrderStatus s = OrderStatus.parse(o.getOrderStatus()).orElse(null);
            return s == OrderStatus.DELIVERED;
        }).count();
        return new OrderSummaryDTO(total, pending, inTransit, completed);
    }

    private OrderDTO toOrderDTOWithItems(Order order) {
        OrderDTO dto = orderMapper.toDTO(order);
        dto.setOrderItems(
                orderItemRepository.findByOrderId(order.getId())
                        .stream()
                        .map(this::toEnrichedItemDTO)
                        .collect(Collectors.toList())
        );
        // Enrich with customer info. Prefer the values snapshotted on the ORDER
        // (e.g. a phone/address the customer edited at/after checkout) over the
        // profile, falling back to the profile when the order has none.
        if (order.getClientId() != null) {
            usersRepository.findById(order.getClientId()).ifPresent(user -> {
                String orderName = order.getCustomerName();
                dto.setCustomerName((orderName != null && !orderName.isBlank())
                        ? orderName : buildName(user));
                String orderPhone = order.getCustomerPhone();
                dto.setCustomerPhone((orderPhone != null && !orderPhone.isBlank())
                        ? orderPhone : user.getPhoneNumber());
            });
        }
        // Enrich with rider info
        if (order.getRiderId() != null) {
            userRiderRepository.findById(order.getRiderId()).ifPresent(rider -> {
                dto.setRiderName(rider.getName());
                dto.setRiderPhone(rider.getContactPhone() != null ? rider.getContactPhone() : rider.getContactNo());
                dto.setRiderImageUrl(rider.getImageUrl());
                dto.setRiderVehicleType(rider.getVehicleType());
                dto.setRiderPlateNumber(rider.getPlateNumber());
                dto.setRiderRating(rider.getRating());
            });
        }
        return dto;
    }

    private String buildName(Users user) {
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last = user.getLastName() != null ? user.getLastName() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? user.getPhoneNumber() : full;
    }

    private OrderItemDTO toEnrichedItemDTO(OrderItem item) {
        OrderItemDTO dto = orderItemMapper.toDTO(item);
        if (item.getProductId() != null) {
            productRepository.findById(item.getProductId()).ifPresent(product -> {
                dto.setProductName(product.getName());
                dto.setImageUrl(product.getImageUrl());
            });
        }
        return dto;
    }
}
