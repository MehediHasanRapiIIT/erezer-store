package kn.org.deliverybackend.mapper;

import kn.org.deliverybackend.dto.UserRiderDTO;
import kn.org.deliverybackend.entity.UserRider;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserRiderMapper {

    UserRiderDTO toDTO(UserRider userRider);

    UserRider toEntity(UserRiderDTO userRiderDTO);
}
