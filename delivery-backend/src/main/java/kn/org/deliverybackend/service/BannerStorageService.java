package kn.org.deliverybackend.service;

import org.springframework.web.multipart.MultipartFile;

public interface BannerStorageService {
    String uploadBanner(MultipartFile file);
    void deleteBanner(String fileName);
}
