package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.BrandRequest;
import com.fashionstore.catalog.dto.BrandResponse;
import com.fashionstore.catalog.service.BrandService;
import com.fashionstore.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BrandController {

    BrandService brandService;

    @GetMapping("/api/v1/brands")
    public ApiResponse<List<BrandResponse>> list() {
        return ApiResponse.<List<BrandResponse>>builder()
                .message("Get brands successfully")
                .data(brandService.getAll()).build();
    }

    @GetMapping("/api/v1/brands/{id}")
    public ApiResponse<BrandResponse> get(@PathVariable String id) {
        return ApiResponse.<BrandResponse>builder()
                .message("Get brand successfully")
                .data(brandService.getById(id)).build();
    }

    @GetMapping("/admin/brands")
    public ApiResponse<List<BrandResponse>> listForAdmin() {
        return ApiResponse.<List<BrandResponse>>builder()
                .message("Get brands successfully")
                .data(brandService.getAllForAdmin()).build();
    }

    @PostMapping("/admin/brands")
    public ApiResponse<BrandResponse> create(@Valid @RequestBody BrandRequest request) {
        return ApiResponse.<BrandResponse>builder()
                .message("Create brand successfully")
                .data(brandService.create(request)).build();
    }

    @PutMapping("/admin/brands/{id}")
    public ApiResponse<BrandResponse> update(@PathVariable String id, @Valid @RequestBody BrandRequest request) {
        return ApiResponse.<BrandResponse>builder()
                .message("Update brand successfully")
                .data(brandService.update(id, request)).build();
    }

    @DeleteMapping("/admin/brands/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        brandService.delete(id);
        return ApiResponse.<Void>builder().message("Delete brand successfully").build();
    }
}
