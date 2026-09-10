package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.SizeOptionRequest;
import com.fashionstore.catalog.dto.SizeOptionResponse;
import com.fashionstore.catalog.service.SizeOptionService;
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
public class SizeOptionController {

    SizeOptionService sizeOptionService;

    @GetMapping({"/api/v1/size-options", "/api/v1/size-options/active"})
    public ApiResponse<List<SizeOptionResponse>> listActive() {
        return ApiResponse.<List<SizeOptionResponse>>builder()
                .message("Get size options successfully")
                .data(sizeOptionService.getAllOptionActive()).build();
    }

    @GetMapping("/admin/size-options")
    public ApiResponse<PageResponse<List<SizeOptionResponse>>> listAll(
            @PageableDefault(page = 0, size = 20, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.<PageResponse<List<SizeOptionResponse>>>builder()
                .message("Get size options successfully")
                .data(sizeOptionService.getAllOptions(pageable)).build();
    }

    @PostMapping("/admin/size-options")
    public ApiResponse<SizeOptionResponse> create(@Valid @RequestBody SizeOptionRequest request) {
        return ApiResponse.<SizeOptionResponse>builder()
                .message("Create size option successfully")
                .data(sizeOptionService.create(request)).build();
    }

    @PutMapping("/admin/size-options/{id}")
    public ApiResponse<SizeOptionResponse> update(@PathVariable String id, @Valid @RequestBody SizeOptionRequest request) {
        return ApiResponse.<SizeOptionResponse>builder()
                .message("Update size option successfully")
                .data(sizeOptionService.update(request, id)).build();
    }

    @DeleteMapping("/admin/size-options/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        sizeOptionService.delete(id);
        return ApiResponse.<Void>builder().message("Delete size option successfully").build();
    }
}
