package kn.org.deliverybackend.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Locks a secret away before it is written to the database, so a copy of the
 * database alone (a backup, a dump) does not hand over a working key.
 *
 * <p>AES-GCM, with the key taken from the server's own secret. The stored form is
 * {@code v1:<base64 nonce+ciphertext>}. Change the server secret and the stored
 * value can no longer be read — the owner simply enters the token again.
 */
@Component
@Slf4j
public class SecretBox {

    private static final String PREFIX = "v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretBox(@Value("${app.secrets.key}") String secret) {
        // Any length of secret becomes a 256-bit key.
        this.key = new SecretKeySpec(sha256(secret), "AES");
    }

    /** @return the locked form, or null for nothing to lock. */
    public String lock(String plain) {
        if (plain == null || plain.isBlank()) return null;
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[nonce.length + sealed.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(sealed, 0, out, nonce.length, sealed.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not store that secret safely.", ex);
        }
    }

    /** @return the original text, or null when there is nothing stored or it can't be read. */
    public String unlock(String stored) {
        if (stored == null || stored.isBlank()) return null;
        if (!stored.startsWith(PREFIX)) {
            log.warn("Stored secret is in an unknown format; ignoring it.");
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] nonce = Arrays.copyOfRange(raw, 0, NONCE_BYTES);
            byte[] sealed = Arrays.copyOfRange(raw, NONCE_BYTES, raw.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            // Usually the server secret changed since it was stored.
            log.warn("Stored secret could not be read back: {}", ex.getMessage());
            return null;
        }
    }

    /** The last few characters, for showing which key is saved without revealing it. */
    public static String hint(String plain) {
        if (plain == null || plain.isBlank()) return null;
        String trimmed = plain.trim();
        return trimmed.length() <= 4 ? "…" : "…" + trimmed.substring(trimmed.length() - 4);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
