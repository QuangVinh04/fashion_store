package com.fashionstore.clothes_retail_api.modules.product.controller;

import com.fashionstore.clothes_retail_api.common.dto.ApiResponse;
import com.fashionstore.clothes_retail_api.common.dto.PageResponse;
import com.fashionstore.clothes_retail_api.modules.product.dto.ProductRequest;
import com.fashionstore.clothes_retail_api.modules.product.dto.ProductResponse;
import com.fashionstore.clothes_retail_api.modules.product.dto.ProductVariantRequest;
import com.fashionstore.clothes_retail_api.modules.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductController {
    ProductService productService;

    @PostMapping("/")
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ApiResponse.<ProductResponse>builder()
                .message("Create product successfully")
                .data(response)
                .build();

    }
    @PostMapping("/{id}")
    public ApiResponse<ProductResponse> update(@Valid @RequestBody ProductRequest request,
                                               @PathVariable String id) {
        ProductResponse response = productService.updateProduct(id, request);
        return ApiResponse.<ProductResponse>builder()
                .message("update product successfully")
                .data(response)
                .build();

    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable String id) {
        ProductResponse response = productService.getProductById(id);
        return ApiResponse.<ProductResponse>builder()
                .message("Get product by id successfully")
                .data(response)
                .build();

    }

    @GetMapping("/all")
    public ApiResponse<PageResponse> getAll(
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PageResponse response = productService.getAllProducts(pageable);
        return ApiResponse.<PageResponse>builder()
                .message("Get all product successfully")
                .data(response)
                .build();

    }
    @GetMapping("/search")
    public ApiResponse<PageResponse> searchByKeyword(
            @RequestParam String keyword,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PageResponse response = productService.searchProducts(pageable, keyword);
        return ApiResponse.<PageResponse>builder()
                .message("Search product by keyword successfully")
                .data(response)
                .build();

    }

    @GetMapping("/category/{categoryId}")
    public ApiResponse<PageResponse> getAllByCategory(
            @PathVariable String categoryId,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PageResponse response = productService.getAllProductsByCategory(pageable, categoryId);
        return ApiResponse.<PageResponse>builder()
                .message("Get all product by category successfully")
                .data(response)
                .build();

    }

    @GetMapping("/advance-search")
    public ApiResponse<PageResponse> advanceSearch(
            @RequestParam(value = "search", required = false) String[] product,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PageResponse response = productService.advanceSearchWithSpecifications(pageable, product);
        return ApiResponse.<PageResponse>builder()
                .message("Search product successfully")
                .data(response)
                .build();

    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        productService.deleteProduct(id);
        return ApiResponse.<Void>builder()
                .message("delete product successfully")
                .build();

    }

    @PostMapping("/{productId}/variants")
    public ApiResponse<ProductResponse> addVariant(
            @PathVariable String productId,
            @Valid @RequestBody ProductVariantRequest request) {
        ProductResponse response = productService.addProductVariant(productId, request);
        return ApiResponse.<ProductResponse>builder()
                .message("add product variant successfully")
                .data(response)
                .build();

    }

    @PostMapping("/{productId}/variants/{variantId}")
    public ApiResponse<ProductResponse> addVariant(
            @PathVariable String productId,
            @PathVariable String variantId,
            @Valid @RequestBody ProductVariantRequest request) {
        ProductResponse response = productService.updateProductVariant(productId, variantId, request);
        return ApiResponse.<ProductResponse>builder()
                .message("update product variant successfully")
                .data(response)
                .build();

    }

    @DeleteMapping("/{productId}/variants/{variantId}")
    public ApiResponse<Void> delete(@PathVariable String productId,
                                    @PathVariable String variantId) {
        productService.deleteProductVariant(productId, variantId);
        return ApiResponse.<Void>builder()
                .message("remove product variant successfully")
                .build();

    }








}
