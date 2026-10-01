package kn.org.deliverybackend.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
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

