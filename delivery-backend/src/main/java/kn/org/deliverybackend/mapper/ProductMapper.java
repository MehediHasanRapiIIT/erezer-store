package kn.org.deliverybackend.mapper;

import kn.org.deliverybackend.dto.request.product.ProductRequestDTO;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "stockStatus", ignore = true)
    ProductResponseDTO toResponseDTO(Product product);

    @Mapping(target = "discountPrice", ignore = true)
    Product toEntity(ProductRequestDTO productRequestDTO);
}
