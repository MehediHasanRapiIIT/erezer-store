package kn.org.deliverybackend.mapper;

import kn.org.deliverybackend.dto.RiderEarningsDTO;
import kn.org.deliverybackend.entity.RiderEarnings;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RiderEarningsMapper {

    RiderEarningsDTO toDTO(RiderEarnings riderEarnings);

    RiderEarnings toEntity(RiderEarningsDTO riderEarningsDTO);
}
