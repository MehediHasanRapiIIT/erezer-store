package kn.org.deliverybackend.mapper;

import kn.org.deliverybackend.dto.OtpVerificationDTO;
import kn.org.deliverybackend.entity.OtpVerification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OtpVerificationMapper {

    OtpVerificationDTO toDTO(OtpVerification otpVerification);

    OtpVerification toEntity(OtpVerificationDTO otpVerificationDTO);
}
