package kn.org.deliverybackend.service.impl;

import io.minio.*;
import kn.org.deliverybackend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageServiceImpl implements FileStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String defaultBucket;

    /**
     * Where *this JVM* reaches MinIO. Inside Docker that is the service name
     * (http://minio:9000), which a browser cannot resolve.
     */
    @Value("${minio.url}")
    private String minioUrl;

    /**
     * Where a *browser* reaches MinIO. Defaults to {@code minio.url} so a plain
     * localhost dev setup keeps working untouched; the containerised stack sets
     * it to the published host URL (http://localhost:9000) so the URLs handed
     * back to the frontend actually load.
     */
    @Value("${minio.public-url:${minio.url}}")
    private String minioPublicUrl;

    @Override
    public String uploadFile(MultipartFile file) {
        return uploadFile(file, defaultBucket);
    }

    @Override
    public String uploadFile(MultipartFile file, String bucket) {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }

            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();

            try (InputStream is = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(fileName)
                                .stream(is, file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build()
                );
            }

            // Public URL, not the internal endpoint - this string is persisted and
            // served to the browser.
            return trimTrailingSlash(minioPublicUrl) + "/" + bucket + "/" + fileName;

        } catch (Exception e) {
            log.error("Error uploading file to MinIO bucket: {}", bucket, e);
            throw new RuntimeException("Could not upload file: " + e.getMessage());
        }
    }

    @Override
    public void deleteFile(String fileName, String bucket) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(fileName)
                            .build()
            );
        } catch (Exception e) {
            log.error("Error deleting file from MinIO bucket: {}", bucket, e);
            throw new RuntimeException("Could not delete file: " + e.getMessage());
        }
    }

    /** Guards against a configured URL with a trailing slash producing "//bucket". */
    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
