package kn.org.deliverybackend.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String uploadFile(MultipartFile file);
    String uploadFile(MultipartFile file, String bucket);
    void deleteFile(String fileName, String bucket);
}

