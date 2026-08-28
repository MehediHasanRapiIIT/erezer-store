package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.dto.HomePageResponseDTO;
import kn.org.deliverybackend.service.HomePageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/app/home")
@RequiredArgsConstructor
@Tag(name = "Home Page", description = "API endpoints for home page data")
public class HomePageController {

    private final HomePageService homePageService;

    @GetMapping
    @Operation(summary = "Get Home Page Data", description = "Retrieves all home page data including banners, categories, and popular items")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Home page data retrieved successfully",
            content = @Content(schema = @Schema(implementation = HomePageResponseDTO.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<HomePageResponseDTO> getHomePage() {
        HomePageResponseDTO response = homePageService.getHomePageData();
        return ResponseEntity.ok(response);
    }
}
