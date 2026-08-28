package kn.org.deliverybackend.mapper;

import kn.org.deliverybackend.dto.AdminUserDTO;
import kn.org.deliverybackend.entity.AdminUser;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdminUserMapper {

    AdminUserDTO toDTO(AdminUser adminUser);

    AdminUser toEntity(AdminUserDTO adminUserDTO);
}
