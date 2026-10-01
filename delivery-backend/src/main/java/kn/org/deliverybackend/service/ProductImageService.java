package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.productimage.ProductImageDTO;
import kn.org.deliverybackend.dto.productimage.ProductImageMetadataDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductImageService {

    List<ProductImageDTO> listForProduct(Long productId);

    ProductImageDTO upload(Long productId, MultipartFile file, String altText, Integer sortOrder, Boolean isPrimary);

    /**
     * Records a picture already put in storage. Splitting storing from recording
     * lets a caller keep hold of every stored address, so it can remove them all
     * if the save they belong to is abandoned.
     */
    ProductImageDTO attach(Long productId, String url, String altText, Integer sortOrder, Boolean isPrimary);

    ProductImageDTO updateMetadata(Long productId, Long imageId, ProductImageMetadataDTO metadata);

    void delete(Long productId, Long imageId);
}
