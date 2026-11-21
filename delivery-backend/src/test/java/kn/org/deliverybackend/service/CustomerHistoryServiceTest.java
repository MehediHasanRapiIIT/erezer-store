package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.order.CustomerHistoryDTO;
import kn.org.deliverybackend.mapper.OrderMapper;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.service.impl.CustomerHistoryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The order list's customer history counts: other orders from the same phone, by status. */
class CustomerHistoryServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final CustomerHistoryService service = new CustomerHistoryService(orders, mock(OrderMapper.class));

    @Test
    void countsDeliveredCancelledReturnedAndOpenOrdersPerOrder() {
        UUID trusted = UUID.randomUUID();
        UUID risky = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        when(orders.countSamePhoneOrders(anyList())).thenReturn(List.of(
                new Object[]{trusted.toString(), "DELIVERED", 5L},
                new Object[]{trusted.toString(), "SHIPPED", 1L},
                new Object[]{risky.toString(), "CANCELLED", 2L},
                new Object[]{risky.toString(), "RETURNED", 1L},
                new Object[]{risky.toString(), "PENDING", 1L}));

        Map<UUID, CustomerHistoryDTO> counts = service.countsFor(List.of(trusted, risky, fresh));

        assertThat(counts.get(trusted)).isEqualTo(new CustomerHistoryDTO(5, 0, 0, 1, List.of()));
        assertThat(counts.get(risky)).isEqualTo(new CustomerHistoryDTO(0, 2, 1, 1, List.of()));
        // First order from this number: everything zero, but still answered.
        assertThat(counts.get(fresh).total()).isZero();
    }

    @Test
    void noIdsAsksNothing() {
        assertThat(service.countsFor(List.of())).isEmpty();
    }
}
