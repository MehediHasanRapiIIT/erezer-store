package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.OTPRequestDTO;
import kn.org.deliverybackend.dto.OTPVerifyRequestDTO;
import kn.org.deliverybackend.dto.OTPVerifyResponseDTO;

public interface OTPService {
    void generateOTP(OTPRequestDTO request);
    OTPVerifyResponseDTO verifyOTP(OTPVerifyRequestDTO request);
}
