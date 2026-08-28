package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.dto.*;
import kn.org.deliverybackend.service.OTPService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/app/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "API endpoints for user authentication")
public class AuthController {

    private final OTPService otpService;

    @PostMapping("/phone/login")
    @Operation(summary = "Phone Login - Initiate authentication", description = "Sends OTP to the provided phone number")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OTP sent successfully",
            content = @Content(schema = @Schema(implementation = LoginResponseDTO.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<LoginResponseDTO> phoneLogin(@RequestBody LoginRequestDTO request) {
        // For now, just generate OTP
        otpService.generateOTP(new OTPRequestDTO(request.getPhoneNumber()));
        
        LoginResponseDTO response = new LoginResponseDTO();
        response.setPhoneNumber(request.getPhoneNumber());
        response.setIsNewUser(true); // Will be updated after OTP verification
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/otp/generate")
    @Operation(summary = "Generate OTP", description = "Generates and sends OTP to the provided phone number")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OTP generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid phone number"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> generateOTP(@RequestBody OTPRequestDTO request) {
        otpService.generateOTP(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify OTP", description = "Verifies the OTP and authenticates the user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OTP verified successfully",
            content = @Content(schema = @Schema(implementation = OTPVerifyResponseDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid OTP or phone number"),
        @ApiResponse(responseCode = "404", description = "OTP not found or expired"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OTPVerifyResponseDTO> verifyOTP(@RequestBody OTPVerifyRequestDTO request) {
        OTPVerifyResponseDTO response = otpService.verifyOTP(request);
        return ResponseEntity.ok(response);
    }
}
