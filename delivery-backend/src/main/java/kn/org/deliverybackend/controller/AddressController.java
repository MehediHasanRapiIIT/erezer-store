package kn.org.deliverybackend.controller;

import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.AddressesDTO;
import kn.org.deliverybackend.service.AddressService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/app/consumer")
@RequiredArgsConstructor
@Tag(name = "Address Management", description = "API endpoints for managing user addresses")
public class AddressController {

    private final AddressService addressService;

    @GetMapping("/{userId}/addresses")
    @Operation(summary = "Get User Addresses", description = "Retrieves all active addresses for a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Addresses retrieved successfully",
            content = @Content(schema = @Schema(implementation = AddressesDTO.class))),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<AddressesDTO>> getAddresses(@PathVariable UUID userId) {
        List<AddressesDTO> addresses = addressService.getAddresses(userId);
        return ResponseEntity.ok(addresses);
    }

    @PostMapping("/{userId}/addresses")
    @Operation(summary = "Add New Address", description = "Adds a new address for the user (max 3 addresses allowed)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Address created successfully",
            content = @Content(schema = @Schema(implementation = AddressesDTO.class))),
        @ApiResponse(responseCode = "400", description = "Address limit exceeded (max 3)"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<AddressesDTO> addAddress(
            @PathVariable UUID userId,
            @Valid @RequestBody AddressesDTO addressesDTO) {
        AddressesDTO createdAddress = addressService.addAddress(userId, addressesDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAddress);
    }

    @PutMapping("/{userId}/addresses/{addressId}")
    @Operation(summary = "Update Address", description = "Updates an existing address for the user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Address updated successfully",
            content = @Content(schema = @Schema(implementation = AddressesDTO.class))),
        @ApiResponse(responseCode = "404", description = "User or address not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<AddressesDTO> updateAddress(
            @PathVariable UUID userId,
            @PathVariable UUID addressId,
            @Valid @RequestBody AddressesDTO addressesDTO) {
        AddressesDTO updatedAddress = addressService.updateAddress(userId, addressId, addressesDTO);
        return ResponseEntity.ok(updatedAddress);
    }

    @DeleteMapping("/{userId}/addresses/{addressId}")
    @Operation(summary = "Delete Address", description = "Soft deletes an address for the user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Address deleted successfully"),
        @ApiResponse(responseCode = "404", description = "User or address not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> deleteAddress(
            @PathVariable UUID userId,
            @PathVariable UUID addressId) {
        addressService.deleteAddress(userId, addressId);
        return ResponseEntity.noContent().build();
    }
}
