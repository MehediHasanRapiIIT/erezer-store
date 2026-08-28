package kn.org.deliverybackend.mapper;

import kn.org.deliverybackend.dto.PaymentDTO;
import kn.org.deliverybackend.entity.Payment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentDTO toDTO(Payment payment);

    Payment toEntity(PaymentDTO paymentDTO);
}
