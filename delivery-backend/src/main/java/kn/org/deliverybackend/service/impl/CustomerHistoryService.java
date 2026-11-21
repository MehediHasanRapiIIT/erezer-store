package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.OrderDTO;
import kn.org.deliverybackend.dto.order.CustomerHistoryDTO;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.mapper.OrderMapper;
import kn.org.deliverybackend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Customer history for staff: the other orders placed from an order's phone
 * number (orders.phone_key, the number's last 10 digits, so "+880 1712-345678"
 * and "01712345678" are one customer). Helps tell an old, trusted customer
 * from one who has cancelled or refused orders before.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerHistoryService {

    /** The most orders one list request may ask about (a page of the order list). */
    public static final int MAX_IDS = 200;

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    /** For the order list: each order's counts, without the orders themselves. */
    public Map<UUID, CustomerHistoryDTO> countsFor(List<UUID> orderIds) {
        Map<UUID, int[]> counts = new HashMap<>();
        if (orderIds == null || orderIds.isEmpty()) return Map.of();
        List<UUID> ids = orderIds.stream().distinct().limit(MAX_IDS).toList();
        for (UUID id : ids) counts.put(id, new int[4]);
        for (Object[] row : orderRepository.countSamePhoneOrders(ids)) {
            int[] c = counts.get(UUID.fromString((String) row[0]));
            if (c != null) c[bucket((String) row[1])] += ((Number) row[2]).intValue();
        }
        Map<UUID, CustomerHistoryDTO> result = new HashMap<>();
        counts.forEach((id, c) -> result.put(id, new CustomerHistoryDTO(c[0], c[1], c[2], c[3], List.of())));
        return result;
    }

    /** For the order page: the counts and every other order from the same number, newest first. */
    public CustomerHistoryDTO historyFor(UUID orderId) {
        List<Order> others = orderRepository.findSamePhoneOrders(orderId);
        int[] c = new int[4];
        List<CustomerHistoryDTO.Entry> entries = others.stream().map(o -> {
            c[bucket(o.getOrderStatus())]++;
            OrderDTO dto = orderMapper.toDTO(o);
            return new CustomerHistoryDTO.Entry(o.getId(), o.getOrderNumber(), dto.getCreatedAt(),
                    o.getTotalAmount(), o.getOrderStatus());
        }).toList();
        return new CustomerHistoryDTO(c[0], c[1], c[2], c[3], entries);
    }

    /** 0 delivered, 1 cancelled, 2 returned, 3 still on its way. */
    static int bucket(String status) {
        if (status == null) return 3;
        return switch (status.toUpperCase()) {
            case "DELIVERED" -> 0;
            case "CANCELLED" -> 1;
            case "RETURNED" -> 2;
            default -> 3;
        };
    }
}
