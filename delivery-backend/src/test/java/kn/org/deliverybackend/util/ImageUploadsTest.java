package kn.org.deliverybackend.util;

import kn.org.deliverybackend.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Uploads are judged by their bytes, not their name or claimed type. */
class ImageUploadsTest {

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void realPhotoFormatsAreAccepted() {
        assertEquals("image/jpeg", ImageUploads.check(100, bytes(0xFF, 0xD8, 0xFF, 0xE0)).contentType());
        assertEquals("png", ImageUploads.check(100, bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)).extension());
        assertEquals("image/gif", ImageUploads.check(100, ascii("GIF89a")).contentType());
        assertEquals("image/webp", ImageUploads.check(100, ascii("RIFF\0\0\0\0WEBPVP8 ")).contentType());
        assertEquals("image/heic", ImageUploads.check(100, ascii("\0\0\0ftypheic")).contentType());
        assertEquals("image/avif", ImageUploads.check(100, ascii("\0\0\0ftypavif")).contentType());
    }

    @Test
    void pagesScriptsAndSvgAreRefused() {
        assertThrows(InvalidRequestException.class, () -> ImageUploads.check(100, ascii("<html><script>alert(1)")));
        assertThrows(InvalidRequestException.class, () -> ImageUploads.check(100, ascii("<?xml version=\"1.0\"?><svg")));
        assertThrows(InvalidRequestException.class, () -> ImageUploads.check(100, ascii("<svg onload=alert(1)>")));
        assertThrows(InvalidRequestException.class, () -> ImageUploads.check(100, ascii("%PDF-1.7")));
    }

    @Test
    void emptyAndOversizedFilesAreRefused() {
        assertThrows(InvalidRequestException.class, () -> ImageUploads.check(0, new byte[0]));
        assertThrows(InvalidRequestException.class,
                () -> ImageUploads.check(ImageUploads.MAX_BYTES + 1, bytes(0xFF, 0xD8, 0xFF)));
    }
}
