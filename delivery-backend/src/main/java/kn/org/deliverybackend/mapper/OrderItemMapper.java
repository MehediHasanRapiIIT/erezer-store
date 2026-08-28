package kn.org.deliverybackend.mapper;

import kn.org.deliverybackend.dto.OrderItemDTO;
import kn.org.deliverybackend.entity.OrderItem;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {

    OrderItemDTO toDTO(OrderItem orderItem);

    OrderItem toEntity(OrderItemDTO orderItemDTO);
}
