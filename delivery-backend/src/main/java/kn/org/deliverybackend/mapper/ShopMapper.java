package kn.org.deliverybackend.mapper;

import kn.org.deliverybackend.dto.ShopDTO;
import kn.org.deliverybackend.entity.Shop;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ShopMapper {

    ShopDTO toDTO(Shop shop);

    Shop toEntity(ShopDTO shopDTO);
}
