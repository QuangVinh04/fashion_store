package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.ColorOptionRequest;
import com.fashionstore.catalog.dto.ColorOptionResponse;
import com.fashionstore.catalog.service.ColorOptionService;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ColorOptionController {

    ColorOptionService colorOptionService;

    @GetMapping({"/api/v1/color-options", "/api/v1/color-options/active"})
    public ApiResponse<List<ColorOptionResponse>> listActive() {
        return ApiResponse.<List<ColorOptionResponse>>builder()
                .message("Get color options successfully")
                .data(colorOptionService.getAllOptionActive()).build();
    }

    @GetMapping("/admin/color-options")
    public ApiResponse<PageResponse<List<ColorOptionResponse>>> listAll(
            @PageableDefault(page = 0, size = 20, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.<PageResponse<List<ColorOptionResponse>>>builder()
                .message("Get color options successfully")
                .data(colorOptionService.getAllOptions(pageable)).build();
    }

    @PostMapping("/admin/color-options")
    public ApiResponse<ColorOptionResponse> create(@Valid @RequestBody ColorOptionRequest request) {
        return ApiResponse.<ColorOptionResponse>builder()
                .message("Create color option successfully")
                .data(colorOptionService.create(request)).build();
    }

    @PutMapping("/admin/color-options/{id}")
    public ApiResponse<ColorOptionResponse> update(@PathVariable String id, @Valid @RequestBody ColorOptionRequest request) {
        return ApiResponse.<ColorOptionResponse>builder()
                .message("Update color option successfully")
                .data(colorOptionService.update(request, id)).build();
    }

    @DeleteMapping("/admin/color-options/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        colorOptionService.delete(id);
        return ApiResponse.<Void>builder().message("Delete color option successfully").build();
    }
}
