package kn.org.deliverybackend.mapper;

import kn.org.deliverybackend.dto.AddressesDTO;
import kn.org.deliverybackend.entity.Addresses;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AddressesMapper {
    AddressesDTO toDTO(Addresses addresses);
    Addresses toEntity(AddressesDTO addressesDTO);
}
