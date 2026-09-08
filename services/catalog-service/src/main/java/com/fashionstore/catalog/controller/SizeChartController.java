package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.*;
import com.fashionstore.catalog.service.SizeChartService;
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
public class SizeChartController {

    SizeChartService sizeChartService;

    @GetMapping("/api/v1/size-charts")
    public ApiResponse<List<SizeChartResponse>> list() {
        return ApiResponse.<List<SizeChartResponse>>builder()
                .message("Get size charts successfully")
                .data(sizeChartService.getAll()).build();
    }

    @GetMapping("/api/v1/size-charts/{id}")
    public ApiResponse<SizeChartResponse> get(@PathVariable String id) {
        return ApiResponse.<SizeChartResponse>builder()
                .message("Get size chart successfully")
                .data(sizeChartService.getById(id)).build();
    }

    @PostMapping("/admin/size-charts")
    public ApiResponse<SizeChartResponse> create(@Valid @RequestBody SizeChartRequest request) {
        return ApiResponse.<SizeChartResponse>builder()
                .message("Create size chart successfully")
                .data(sizeChartService.create(request)).build();
    }

    @PutMapping("/admin/size-charts/{id}")
    public ApiResponse<SizeChartResponse> update(@PathVariable String id, @Valid @RequestBody SizeChartRequest request) {
        return ApiResponse.<SizeChartResponse>builder()
                .message("Update size chart successfully")
                .data(sizeChartService.update(id, request)).build();
    }

    @DeleteMapping("/admin/size-charts/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        sizeChartService.delete(id);
        return ApiResponse.<Void>builder().message("Delete size chart successfully").build();
    }

    // Rows
    @PostMapping("/admin/size-charts/{id}/rows")
    public ApiResponse<SizeChartRowResponse> addRow(@PathVariable String id, @Valid @RequestBody SizeChartRowRequest request) {
        return ApiResponse.<SizeChartRowResponse>builder()
                .message("Add size chart row successfully")
                .data(sizeChartService.addRow(id, request)).build();
    }

    @PutMapping("/admin/size-charts/{id}/rows/{rowId}")
    public ApiResponse<SizeChartRowResponse> updateRow(@PathVariable String id, @PathVariable String rowId, @Valid @RequestBody SizeChartRowRequest request) {
        return ApiResponse.<SizeChartRowResponse>builder()
                .message("Update size chart row successfully")
                .data(sizeChartService.updateRow(id, rowId, request)).build();
    }

    @DeleteMapping("/admin/size-charts/{id}/rows/{rowId}")
    public ApiResponse<Void> deleteRow(@PathVariable String id, @PathVariable String rowId) {
        sizeChartService.deleteRow(id, rowId);
        return ApiResponse.<Void>builder().message("Delete size chart row successfully").build();
    }
}
