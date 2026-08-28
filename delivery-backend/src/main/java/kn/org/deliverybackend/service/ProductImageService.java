package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.productimage.ProductImageDTO;
import kn.org.deliverybackend.dto.productimage.ProductImageMetadataDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductImageService {

    List<ProductImageDTO> listForProduct(Long productId);

    ProductImageDTO upload(Long productId, MultipartFile file, String altText, Integer sortOrder, Boolean isPrimary);

    ProductImageDTO updateMetadata(Long productId, Long imageId, ProductImageMetadataDTO metadata);

    void delete(Long productId, Long imageId);
}
