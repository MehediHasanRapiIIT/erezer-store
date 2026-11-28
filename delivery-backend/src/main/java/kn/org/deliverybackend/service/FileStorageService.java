package kn.org.deliverybackend.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    /** A stored file read back: its name, type and bytes. */
    record StoredFile(String name, String contentType, byte[] bytes) {}

    /**
     * Reads back a file this shop stored, by the address {@link #uploadFile}
     * handed back. Empty for an address that isn't ours, a file that is gone,
     * or one bigger than {@code maxBytes}. Never fetches from anywhere else.
     */
    default java.util.Optional<StoredFile> read(String url, long maxBytes) {
        return java.util.Optional.empty();
    }
    String uploadFile(MultipartFile file);
    String uploadFile(MultipartFile file, String bucket);
    void deleteFile(String fileName, String bucket);

    /**
     * Removes a file given the address {@link #uploadFile} handed back. Used to
     * tidy up after a save that was abandoned, so a picture never outlives the
     * product it was uploaded for.
     */
    void deleteByUrl(String url);
}

