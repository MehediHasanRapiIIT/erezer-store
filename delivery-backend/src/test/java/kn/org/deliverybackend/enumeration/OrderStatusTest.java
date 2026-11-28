package kn.org.deliverybackend.enumeration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The shop's optional "Call confirmed" step after Accepted. */
class OrderStatusTest {

    @Test
    void callConfirmedIsAnOptionalStepAfterAccepted() {
        assertThat(OrderStatus.ACCEPTED.nextStates())
                .containsExactlyInAnyOrder(OrderStatus.CALL_CONFIRMED, OrderStatus.PROCESSING, OrderStatus.CANCELLED);
        assertThat(OrderStatus.CALL_CONFIRMED.nextStates())
                .containsExactlyInAnyOrder(OrderStatus.PROCESSING, OrderStatus.CANCELLED);
        assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.CALL_CONFIRMED)).isFalse();
    }

    @Test
    void theCustomerCanStillCancelAndSeesAccepted() {
        assertThat(OrderStatus.CALL_CONFIRMED.isCancellableByCustomer()).isTrue();
        assertThat(OrderStatus.CALL_CONFIRMED.forCustomer()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(OrderStatus.forCustomer("CALL_CONFIRMED")).isEqualTo("ACCEPTED");
        assertThat(OrderStatus.forCustomer("SHIPPED")).isEqualTo("SHIPPED");
        assertThat(OrderStatus.forCustomer("PENDING")).isEqualTo("PLACED");
    }
}
