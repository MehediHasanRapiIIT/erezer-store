package kn.org.deliverybackend.util;

import kn.org.deliverybackend.exception.InvalidRequestException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

/**
 * Every upload in the shop is a photo, and the files are served publicly from
 * the media host. So a file is only accepted when its first bytes really are a
 * JPEG, PNG, GIF, WebP, AVIF or HEIC image: the name and the type the browser
 * claims are ignored, because both are chosen by whoever sends it. That keeps
 * web pages, scripts and SVGs (which can carry scripts) off the media host.
 */
public final class ImageUploads {

    /** Big enough for a full-size phone photo. */
    public static final long MAX_BYTES = 15L * 1024 * 1024;

    /** What was found: the content type to store and the file extension to use. */
    public record Kind(String contentType, String extension) {
    }

    private static final Set<String> AVIF_BRANDS = Set.of("avif", "avis");
    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1");

    private ImageUploads() {
    }

    /**
     * @param size   the file's size in bytes
     * @param header at least the first 12 bytes of the file (fewer only if the file is shorter)
     */
    public static Kind check(long size, byte[] header) {
        if (size <= 0 || header == null || header.length == 0) {
            throw new InvalidRequestException("The file is empty.");
        }
        if (size > MAX_BYTES) {
            throw new InvalidRequestException("The photo is too large. Please use one under 15 MB.");
        }
        Kind kind = detect(header);
        if (kind == null) {
            throw new InvalidRequestException("Please upload a photo (JPG, PNG, WebP, GIF, AVIF or HEIC).");
        }
        return kind;
    }

    static Kind detect(byte[] h) {
        if (starts(h, 0xFF, 0xD8, 0xFF)) return new Kind("image/jpeg", "jpg");
        if (starts(h, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return new Kind("image/png", "png");
        if (ascii(h, 0, "GIF87a") || ascii(h, 0, "GIF89a")) return new Kind("image/gif", "gif");
        if (ascii(h, 0, "RIFF") && ascii(h, 8, "WEBP")) return new Kind("image/webp", "webp");
        if (ascii(h, 4, "ftyp") && h.length >= 12) {
            String brand = new String(Arrays.copyOfRange(h, 8, 12), StandardCharsets.US_ASCII);
            if (AVIF_BRANDS.contains(brand)) return new Kind("image/avif", "avif");
            if (HEIC_BRANDS.contains(brand)) return new Kind("image/heic", "heic");
        }
        return null;
    }

    private static boolean starts(byte[] h, int... expected) {
        if (h.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((h[i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    private static boolean ascii(byte[] h, int offset, String text) {
        byte[] want = text.getBytes(StandardCharsets.US_ASCII);
        if (h.length < offset + want.length) return false;
        return Arrays.equals(Arrays.copyOfRange(h, offset, offset + want.length), want);
    }
}
