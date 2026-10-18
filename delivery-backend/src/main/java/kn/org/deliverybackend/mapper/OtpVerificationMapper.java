package kn.org.deliverybackend.mapper;

import kn.org.deliverybackend.dto.OtpVerificationDTO;
import kn.org.deliverybackend.entity.OtpVerification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = ShopClockMapping.class)
public interface OtpVerificationMapper {

    OtpVerificationDTO toDTO(OtpVerification otpVerification);

    OtpVerification toEntity(OtpVerificationDTO otpVerificationDTO);
}
