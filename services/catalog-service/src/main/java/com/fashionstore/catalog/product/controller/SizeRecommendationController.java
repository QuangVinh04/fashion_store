package com.fashionstore.catalog.product.controller;

import com.fashionstore.catalog.product.dto.SizeRecommendationRequest;
import com.fashionstore.catalog.product.dto.SizeRecommendationResponse;
import com.fashionstore.catalog.product.service.SizeRecommendationService;
import com.fashionstore.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public size suggestion for a product. It is a GET with query parameters so the existing public
 * GET /api/v1/products/** rule covers it without opening any POST path.
 */
@RestController
@RequiredArgsConstructor
public class SizeRecommendationController {

    private final SizeRecommendationService sizeRecommendationService;

    @GetMapping("/api/v1/products/{id}/size-recommendation")
    public ApiResponse<SizeRecommendationResponse> recommend(@PathVariable String id,
                                                             @Valid SizeRecommendationRequest request) {
        return ApiResponse.<SizeRecommendationResponse>builder()
                .message("Get size recommendation successfully")
                .data(sizeRecommendationService.recommend(id, request))
                .build();
    }
}
