package kn.org.deliverybackend.mapper;

import kn.org.deliverybackend.dto.NotificationsDTO;
import kn.org.deliverybackend.entity.Notifications;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationsMapper {

    NotificationsDTO toDTO(Notifications notifications);

    Notifications toEntity(NotificationsDTO notificationsDTO);
}
