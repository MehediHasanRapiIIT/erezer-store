package kn.org.deliverybackend.service.impl;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A picture is stored exactly as it was uploaded: never shrunk, re-compressed
 * or converted. Its size and quality in the shop are its size and quality on
 * the uploader's computer.
 *
 * <p>Every upload in the shop — products, categories, banners, design-studio
 * artwork, return photos — ends in {@code uploadFile}, so holding this one
 * function to "the bytes in are the bytes stored" holds all of them.
 * {@code deploy/verify_images_unchanged.py} checks the same thing end to end.
 */
class StoredExactlyAsUploadedTest {

    private final MinioClient minio = mock(MinioClient.class);
    private final MinioStorageServiceImpl storage = new MinioStorageServiceImpl(minio);

    /** What reached storage. */
    private final AtomicReference<byte[]> stored = new AtomicReference<>();
    private final AtomicReference<PutObjectArgs> request = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(storage, "defaultBucket", "product-images");
        ReflectionTestUtils.setField(storage, "minioPublicUrl", "http://files");
        when(minio.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minio.putObject(any(PutObjectArgs.class))).thenAnswer(inv -> {
            PutObjectArgs args = inv.getArgument(0);
            request.set(args);
            stored.set(args.stream().readAllBytes());
            return null;
        });
    }

    /** A file of this kind and size: the real opening bytes, then noise no codec would leave alone. */
    private static byte[] picture(int size, int... opening) {
        byte[] bytes = new byte[size];
        new Random(size).nextBytes(bytes);
        for (int i = 0; i < opening.length; i++) bytes[i] = (byte) opening[i];
        return bytes;
    }

    private void assertStoredUntouched(byte[] original, String contentType, String extension) throws Exception {
        String url = storage.uploadFile(new MockMultipartFile("file", "photo", contentType, original));
        assertArrayEquals(original, stored.get(), "what is stored must be the uploaded bytes, unchanged");
        assertEquals(original.length, request.get().objectSize(), "and declared at its true size");
        assertEquals(contentType, request.get().contentType());
        assertTrue(url.endsWith("." + extension), url);
    }

    @Test
    void aJpegIsStoredByteForByte() throws Exception {
        assertStoredUntouched(picture(3_000_000, 0xFF, 0xD8, 0xFF, 0xE0), "image/jpeg", "jpg");
    }

    @Test
    void aPngIsStoredByteForByte() throws Exception {
        assertStoredUntouched(picture(5_000_000, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A), "image/png", "png");
    }

    @Test
    void aWebpIsStoredByteForByteAndStaysAWebp() throws Exception {
        assertStoredUntouched(picture(1_200_000, 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'), "image/webp", "webp");
    }

    @Test
    void aLargePhotoIsNotReduced() {
        // Just under the 15 MB a picture may be.
        byte[] large = picture(14_900_000, 0xFF, 0xD8, 0xFF, 0xE1);
        storage.uploadFile(new MockMultipartFile("file", "big.jpg", "image/jpeg", large));
        assertEquals(large.length, stored.get().length, "nothing may make a large photo smaller");
        assertArrayEquals(large, stored.get());
    }
}
