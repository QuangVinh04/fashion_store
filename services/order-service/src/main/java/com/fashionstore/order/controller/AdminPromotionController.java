package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.order.dto.PromotionRequest;
import com.fashionstore.order.dto.PromotionResponse;
import com.fashionstore.order.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin - Promotions", description = "Quản lý mã khuyến mãi (ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/promotions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminPromotionController {

    PromotionService promotionService;

    @Operation(summary = "Tạo mã khuyến mãi mới (ADMIN)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PromotionResponse> createPromotion(@Valid @RequestBody PromotionRequest request) {
        return ApiResponse.<PromotionResponse>builder()
                .message("Create promotion successfully")
                .data(promotionService.createPromotion(request))
                .build();
    }

    @Operation(summary = "Cập nhật mã khuyến mãi (ADMIN)")
    @PutMapping("/{id}")
    public ApiResponse<PromotionResponse> updatePromotion(
            @PathVariable String id,
            @Valid @RequestBody PromotionRequest request
    ) {
        return ApiResponse.<PromotionResponse>builder()
                .message("Update promotion successfully")
                .data(promotionService.updatePromotion(id, request))
                .build();
    }

    @Operation(summary = "Xem chi tiết khuyến mãi theo ID (ADMIN)")
    @GetMapping("/{id}")
    public ApiResponse<PromotionResponse> getPromotionById(@PathVariable String id) {
        return ApiResponse.<PromotionResponse>builder()
                .message("Get promotion successfully")
                .data(promotionService.getPromotionById(id))
                .build();
    }

    @Operation(summary = "Xem chi tiết khuyến mãi theo code (ADMIN)")
    @GetMapping("/code/{code}")
    public ApiResponse<PromotionResponse> getPromotionByCode(@PathVariable String code) {
        return ApiResponse.<PromotionResponse>builder()
                .message("Get promotion by code successfully")
                .data(promotionService.getPromotionByCode(code))
                .build();
    }

    @Operation(summary = "Danh sách khuyến mãi (ADMIN)")
    @GetMapping
    public ApiResponse<PageResponse<List<PromotionResponse>>> getPromotions(
            @RequestParam(required = false) Boolean active,
            @ParameterObject
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.<PageResponse<List<PromotionResponse>>>builder()
                .message("Get promotions successfully")
                .data(promotionService.getPromotions(active, pageable))
                .build();
    }

    @Operation(summary = "Bật/tắt trạng thái hoạt động của khuyến mãi (ADMIN)")
    @PatchMapping("/{id}/status")
    public ApiResponse<Void> togglePromotionActive(
            @PathVariable String id,
            @RequestParam boolean active
    ) {
        promotionService.togglePromotionActive(id, active);
        return ApiResponse.<Void>builder()
                .message("Toggle promotion status successfully")
                .build();
    }
}
