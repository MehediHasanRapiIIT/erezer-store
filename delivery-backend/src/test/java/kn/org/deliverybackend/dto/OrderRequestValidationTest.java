package kn.org.deliverybackend.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import kn.org.deliverybackend.dto.order.GuestOrderRequestDTO;
import kn.org.deliverybackend.dto.request.order.OrderItemRequestDTO;
import kn.org.deliverybackend.dto.request.order.PlaceOrderRequestDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What an order must contain before the server will look at it. */
class OrderRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static OrderItemRequestDTO item(int quantity) {
        OrderItemRequestDTO i = new OrderItemRequestDTO();
        i.setProductId(1L);
        i.setQuantity(quantity);
        return i;
    }

    private static GuestOrderRequestDTO guest() {
        GuestOrderRequestDTO g = new GuestOrderRequestDTO();
        g.setEmail("rina@example.com");
        g.setFirstName("Rina");
        g.setLastName("Akter");
        g.setDeliveryAddress("House 12, Road 4, Dhanmondi, Dhaka");
        g.setPhone("01712-345678");
        g.setPaymentMethod("CASH");
        g.setItems(List.of(item(2)));
        return g;
    }

    private Set<String> badFields(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void aNormalGuestOrderPasses() {
        assertEquals(Set.of(), badFields(guest()));
    }

    @Test
    void phonePaymentAddressAndQuantityAreChecked() {
        GuestOrderRequestDTO g = guest();
        g.setPhone("call me");
        g.setPaymentMethod("FREE");
        g.setDeliveryAddress("x".repeat(256));
        g.setItems(List.of(item(1000)));
        assertEquals(Set.of("phone", "paymentMethod", "deliveryAddress", "items[0].quantity"), badFields(g));
    }

    @Test
    void aSignedInOrderNeedsAddressPhoneAndPayment() {
        PlaceOrderRequestDTO p = new PlaceOrderRequestDTO();
        p.setItems(List.of(item(1)));
        Set<String> bad = badFields(p);
        assertTrue(bad.containsAll(Set.of("deliveryAddress", "phone", "paymentMethod")), bad.toString());
    }

    @Test
    void tooManyLinesAreRefused() {
        GuestOrderRequestDTO g = guest();
        g.setItems(java.util.Collections.nCopies(51, item(1)));
        assertEquals(Set.of("items"), badFields(g));
    }
}
