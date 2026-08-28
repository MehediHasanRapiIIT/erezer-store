package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.service.BannerStorageService;
import kn.org.deliverybackend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MinioBannerStorageServiceImpl implements BannerStorageService {

    private final FileStorageService fileStorageService;

    @Value("${minio.banner-bucket-name}")
    private String bannerBucket;

    @Override
    public String uploadBanner(MultipartFile file) {
        return fileStorageService.uploadFile(file, bannerBucket);
    }

    @Override
    public void deleteBanner(String fileName) {
        fileStorageService.deleteFile(fileName, bannerBucket);
    }
}
