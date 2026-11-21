package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.Users;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.CustomerDataRepository;
import kn.org.deliverybackend.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** A customer deleting their account: password first, nothing on its way, then the person is taken out. */
class AccountDeletionTest {

    private final UsersRepository users = mock(UsersRepository.class);
    private final CustomerDataRepository data = mock(CustomerDataRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AccountDeletionServiceImpl service = new AccountDeletionServiceImpl(users, data, encoder);
    private final UUID me = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Users account = new Users();
        account.setId(me);
        account.setEmail("rina@example.com");
        account.setPasswordHash("hash");
        when(users.findById(me)).thenReturn(Optional.of(account));
        when(encoder.matches("right-pass", "hash")).thenReturn(true);
        when(data.openOrderNumbers(eq(me), any())).thenReturn(List.of());
        when(data.openCustomOrders(eq(me), any())).thenReturn(List.of());
    }

    @Test
    void deletesTheAccountAndKeepsOrdersWithoutThePerson() {
        service.deleteAccount(me, "right-pass");

        verify(data).anonymizeOrders(me, "Deleted customer", AccountDeletionServiceImpl.REMOVED_ADDRESS);
        verify(data).anonymizeCustomOrders(me, AccountDeletionServiceImpl.REMOVED_EMAIL, AccountDeletionServiceImpl.REMOVED_ADDRESS);
        verify(data).anonymizeReturns(me);
        verify(data).deleteAddresses(me);
        verify(data).deleteCart(me);
        verify(data).deleteDesignDrafts(me);
        verify(data).deleteNotifications(me);
        verify(data).deleteOneTimeCodes(me);
        verify(data).deletePushDevices(me);
        verify(data).deleteNewsletterSignup("rina@example.com");
        verify(data).deleteAccount(me);
    }

    @Test
    void aWrongPasswordChangesNothing() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> service.deleteAccount(me, "wrong"));
        assertEquals("That password is not right.", e.getMessage());
        verify(data, never()).deleteAccount(any());
        verify(data, never()).anonymizeOrders(any(), anyString(), anyString());
    }

    @Test
    void anOrderStillOnItsWayStopsIt() {
        when(data.openOrderNumbers(eq(me), any())).thenReturn(List.of("EZ-123456"));
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> service.deleteAccount(me, "right-pass"));
        assertTrue(e.getMessage().contains("EZ-123456"));
        verify(data, never()).deleteAccount(any());
    }

    @Test
    void aCustomOrderInProgressStopsIt() {
        when(data.openCustomOrders(eq(me), any())).thenReturn(List.of("CD-1001"));
        assertThrows(InvalidRequestException.class, () -> service.deleteAccount(me, "right-pass"));
        verify(data, never()).deleteAccount(any());
    }
}
