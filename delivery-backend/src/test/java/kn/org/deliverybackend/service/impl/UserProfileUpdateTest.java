package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.UsersDTO;
import kn.org.deliverybackend.entity.Users;
import kn.org.deliverybackend.exception.DuplicateResourceException;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.mapper.AddressesMapper;
import kn.org.deliverybackend.mapper.UsersMapper;
import kn.org.deliverybackend.repository.AddressesRepository;
import kn.org.deliverybackend.repository.UsersRepository;
import kn.org.deliverybackend.service.CustomerAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** A customer's profile save changes only their own name, phone and email. */
class UserProfileUpdateTest {

    private final UsersRepository users = mock(UsersRepository.class);
    private final AddressesRepository addresses = mock(AddressesRepository.class);
    private final UsersMapper usersMapper = mock(UsersMapper.class);
    private final CustomerAuthService auth = mock(CustomerAuthService.class);
    private UserProfileServiceImpl service;

    private final UUID me = UUID.randomUUID();
    private Users account;

    @BeforeEach
    void setUp() {
        service = new UserProfileServiceImpl(users, addresses, usersMapper, mock(AddressesMapper.class), auth);
        account = new Users();
        account.setId(me);
        account.setEmail("rina@example.com");
        account.setFirstName("Rina");
        account.setLastName("Akter");
        account.setPasswordHash("hash");
        account.setIsActive(true);
        account.setEmailVerified(true);
        when(users.findById(me)).thenReturn(Optional.of(account));
        when(users.save(any(Users.class))).thenAnswer(i -> i.getArgument(0));
        when(usersMapper.toDTO(any(Users.class))).thenReturn(new UsersDTO());
        when(addresses.findByConsumerId(me)).thenReturn(List.of());
    }

    @Test
    void updatesThisAccountAndIgnoresAnIdOrFlagsInTheBody() {
        UsersDTO body = new UsersDTO();
        body.setId(UUID.randomUUID());          // someone else's account
        body.setIsActive(false);
        body.setFirstName("  Rina  ");
        body.setLastName("Begum");

        service.updateProfile(me, body);

        ArgumentCaptor<Users> saved = ArgumentCaptor.forClass(Users.class);
        verify(users).save(saved.capture());
        assertSame(account, saved.getValue());
        assertEquals(me, saved.getValue().getId());
        assertEquals("Rina", saved.getValue().getFirstName());
        assertEquals("Begum", saved.getValue().getLastName());
        assertEquals("hash", saved.getValue().getPasswordHash());
        assertTrue(saved.getValue().getIsActive());
        verify(auth, never()).resendVerificationEmail(anyString());
    }

    @Test
    void aNewEmailMustBeVerifiedAgain() {
        UsersDTO body = new UsersDTO();
        body.setEmail(" New@Example.com ");

        service.updateProfile(me, body);

        assertEquals("new@example.com", account.getEmail());
        assertFalse(account.getEmailVerified());
        verify(auth).resendVerificationEmail("new@example.com");
    }

    @Test
    void anEmailInUseOrMalformedIsRefused() {
        when(users.existsByEmailAndIdNot("taken@example.com", me)).thenReturn(true);
        UsersDTO taken = new UsersDTO();
        taken.setEmail("taken@example.com");
        assertThrows(DuplicateResourceException.class, () -> service.updateProfile(me, taken));

        UsersDTO bad = new UsersDTO();
        bad.setEmail("not-an-email");
        assertThrows(InvalidRequestException.class, () -> service.updateProfile(me, bad));

        UsersDTO blankName = new UsersDTO();
        blankName.setFirstName("   ");
        assertThrows(InvalidRequestException.class, () -> service.updateProfile(me, blankName));
        verify(users, never()).save(any());
    }
}
