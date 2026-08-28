package kn.org.deliverybackend.mapper;

import kn.org.deliverybackend.dto.UsersDTO;
import kn.org.deliverybackend.entity.Users;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UsersMapper {

    UsersDTO toDTO(Users users);

    Users toEntity(UsersDTO usersDTO);
}
