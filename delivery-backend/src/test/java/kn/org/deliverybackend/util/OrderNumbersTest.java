package kn.org.deliverybackend.util;

import kn.org.deliverybackend.dto.order.PublicOrderTrackingDTO;
import kn.org.deliverybackend.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Order numbers: how they look, how typed numbers are read, and what tracking may show. */
class OrderNumbersTest {

    @Test
    void newNumbersAreEzAndSixDigitsAndNeverRepeatAnExistingOne() {
        OrderRepository orders = mock(OrderRepository.class);
        when(orders.existsByOrderNumber(anyString())).thenReturn(false);
        OrderNumbers numbers = new OrderNumbers(orders);

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String n = numbers.next();
            assertTrue(n.matches("EZ-[1-9]\\d{5}"), n);
            seen.add(n);
        }
        assertTrue(seen.size() > 150, "numbers are random, not repeated");
    }

    @Test
    void aTakenNumberIsSkipped() {
        OrderRepository orders = mock(OrderRepository.class);
        when(orders.existsByOrderNumber(anyString())).thenReturn(true, true, false);
        String n = new OrderNumbers(orders).next();
        assertTrue(n.startsWith("EZ-"));
    }

    @Test
    void numbersAreReadTheWayCustomersTypeThem() {
        for (String typed : List.of("EZ-482915", "ez-482915", " EZ482915 ", "#EZ-482915", "482915", "ez 482915")) {
            assertEquals(Optional.of("EZ-482915"), OrderNumbers.normalize(typed), typed);
        }
    }

    @Test
    void anythingElseIsNotAnOrderNumber() {
        for (String typed : Arrays.asList(null, "", "EZ-48291", "EZ-4829156", "AB-482915", "482915'; drop", "%")) {
            assertEquals(Optional.empty(), OrderNumbers.normalize(typed), String.valueOf(typed));
        }
    }

    @Test
    void publicTrackingHasNoFieldForPersonalDetails() {
        List<String> fields = new java.util.ArrayList<>();
        for (Class<?> type : List.of(PublicOrderTrackingDTO.class, PublicOrderTrackingDTO.Step.class, PublicOrderTrackingDTO.Item.class)) {
            for (RecordComponent c : type.getRecordComponents()) fields.add(c.getName().toLowerCase());
        }
        // Allowed although they contain "name": the product's name on an item, and the courier company.
        Set<String> allowed = Set.of("name", "couriername");
        for (String forbidden : List.of("name", "phone", "email", "address", "note", "changedby", "client", "customer")) {
            assertTrue(fields.stream().filter(f -> !allowed.contains(f)).noneMatch(f -> f.contains(forbidden)),
                    "tracking must not expose " + forbidden + ": " + fields);
        }
        // "name" is only the product's name, on an item.
        assertEquals(List.of("name"), Arrays.stream(PublicOrderTrackingDTO.Item.class.getRecordComponents())
                .map(RecordComponent::getName).filter("name"::equals).toList());
    }
}
