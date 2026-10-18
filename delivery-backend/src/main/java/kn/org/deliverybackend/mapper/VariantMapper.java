package kn.org.deliverybackend.mapper;

import kn.org.deliverybackend.dto.VariantDTO;
import kn.org.deliverybackend.entity.Variant;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = ShopClockMapping.class)
public interface VariantMapper {

    VariantDTO toDTO(Variant variant);

    Variant toEntity(VariantDTO variantDTO);
}
