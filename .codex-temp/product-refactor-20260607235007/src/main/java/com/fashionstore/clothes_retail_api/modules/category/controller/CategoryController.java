package com.fashionstore.clothes_retail_api.modules.category.controller;


import com.fashionstore.clothes_retail_api.common.dto.ApiResponse;
import com.fashionstore.clothes_retail_api.common.dto.PageResponse;
import com.fashionstore.clothes_retail_api.modules.category.dto.CategoryRequest;
import com.fashionstore.clothes_retail_api.modules.category.dto.CategoryResponse;
import com.fashionstore.clothes_retail_api.modules.category.service.CategoryService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/category")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CategoryController {

    CategoryService categoryService;

    @PostMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.createCategory(request);

        return ApiResponse.<CategoryResponse>builder()
                .message("Create category successfully")
                .data(response).build();


    }
    @PostMapping("/{id}")
    public ApiResponse<CategoryResponse> update(@PathVariable String id, @Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.updateCategory(id, request);

        return ApiResponse.<CategoryResponse>builder()
                .message("Update category successfully")
                .data(response).build();


    }

    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> get(@PathVariable String id) {
        CategoryResponse response = categoryService.getCategoryById(id);

        return ApiResponse.<CategoryResponse>builder()
                .message("get category by id successfully")
                .data(response).build();


    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        categoryService.deleteCategory(id);
        return ApiResponse.<Void>builder()
                .message("Delete category successfully")
                .build();
    }

    @GetMapping("/all")
    public ApiResponse<PageResponse<List<CategoryResponse>>> getAll(
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<List<CategoryResponse>> response = categoryService.getAllCategories(pageable);

        return ApiResponse.<PageResponse<List<CategoryResponse>>>builder()
                .message("get all category successfully")
                .data(response).build();
    }

}
