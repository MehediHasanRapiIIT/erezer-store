package kn.org.deliverybackend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Secrets typed into the admin panel are locked before they reach the database. */
class SecretBoxTest {

    private final SecretBox box = new SecretBox("a-server-secret-of-some-length-123456");

    @Test
    void locksAndReadsBackTheSameSecret() {
        String token = "EAAG1234567890abcdefGHIJKLmnopQRSTuv";
        String stored = box.lock(token);

        assertNotNull(stored);
        assertFalse(stored.contains(token), "the plain token must not be in what is stored");
        assertTrue(stored.startsWith("v1:"));
        assertEquals(token, box.unlock(stored));
    }

    @Test
    void theSameSecretLooksDifferentEachTimeItIsStored() {
        assertNotEquals(box.lock("same-token"), box.lock("same-token"));
    }

    @Test
    void anotherServerSecretCannotReadIt() {
        String stored = box.lock("EAAsecret");
        assertNull(new SecretBox("a-different-server-secret-000000000").unlock(stored));
    }

    @Test
    void nothingToLockOrReadIsHandledQuietly() {
        assertNull(box.lock(null));
        assertNull(box.lock("  "));
        assertNull(box.unlock(null));
        assertNull(box.unlock("not-in-our-format"));
        assertNull(box.unlock("v1:not-base64!!"));
    }

    @Test
    void theHintShowsOnlyTheLastFewCharacters() {
        assertEquals("…STuv", SecretBox.hint("EAAG1234567890abcdefGHIJKLmnopQRSTuv"));
        assertEquals("…", SecretBox.hint("abc"));
        assertNull(SecretBox.hint(null));
    }
}
