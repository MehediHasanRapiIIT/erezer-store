package kn.org.deliverybackend.mapper;

import kn.org.deliverybackend.dto.PromotionalBannerDTO;
import kn.org.deliverybackend.entity.PromotionalBanner;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PromotionalBannerMapper {

    PromotionalBannerDTO toDTO(PromotionalBanner promotionalBanner);

    PromotionalBanner toEntity(PromotionalBannerDTO promotionalBannerDTO);

    List<PromotionalBannerDTO> toDTOs(List<PromotionalBanner> promotionalBanners);

    List<PromotionalBanner> toEntities(List<PromotionalBannerDTO> promotionalBannerDTOS);
}
