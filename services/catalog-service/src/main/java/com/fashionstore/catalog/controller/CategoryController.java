package com.fashionstore.catalog.controller;


import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.catalog.dto.CategoryRequest;
import com.fashionstore.catalog.dto.CategoryResponse;
import com.fashionstore.catalog.dto.ProductSummaryResponse;
import com.fashionstore.catalog.service.CategoryService;
import com.fashionstore.catalog.service.ProductService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping({"/api/v1/categories", "/api/v1/category"})
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CategoryController {

    CategoryService categoryService;
    ProductService productService;

    @PostMapping({"", "/"})
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.createCategory(request);

        return ApiResponse.<CategoryResponse>builder()
                .message("Create category successfully")
                .data(response).build();


    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PUT, RequestMethod.POST})
    public ApiResponse<CategoryResponse> update(@PathVariable String id, @Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.updateCategory(id, request);

        return ApiResponse.<CategoryResponse>builder()
                .message("Update category successfully")
                .data(response).build();


    }

    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategoryTree() {
        return ApiResponse.<List<CategoryResponse>>builder()
                .message("Get category tree successfully")
                .data(categoryService.getCategoryTree())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> get(@PathVariable String id) {
        CategoryResponse response = categoryService.getCategoryById(id);

        return ApiResponse.<CategoryResponse>builder()
                .message("get category by id successfully")
                .data(response).build();


    }

    @GetMapping("/{id}/products")
    public ApiResponse<PageResponse<List<ProductSummaryResponse>>> getProductsByCategory(
            @PathVariable String id,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.<PageResponse<List<ProductSummaryResponse>>>builder()
                .message("Get category products successfully")
                .data(productService.getAllProductsByCategory(pageable, id))
                .build();
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

