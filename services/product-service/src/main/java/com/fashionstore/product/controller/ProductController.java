package com.fashionstore.product.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.product.dto.AssignProductAttributeRequest;
import com.fashionstore.product.dto.ProductRequest;
import com.fashionstore.product.dto.ProductResponse;
import com.fashionstore.product.dto.ProductSummaryResponse;
import com.fashionstore.product.dto.ProductUpdateRequest;
import com.fashionstore.product.dto.ProductVariantBatchRequest;
import com.fashionstore.product.dto.ProductVariantResponse;
import com.fashionstore.product.dto.ProductVariantSnapshotResponse;
import com.fashionstore.product.dto.SizeChartResponse;
import com.fashionstore.product.service.ProductService;
import com.fashionstore.product.service.SizeChartService;
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
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductController {

    ProductService productService;
    SizeChartService sizeChartService;

    @GetMapping({"/api/v1/products", "/api/v1/product"})
    public ApiResponse<PageResponse<List<ProductSummaryResponse>>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryId,
            @RequestParam(value = "search", required = false) String[] search,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PageResponse<List<ProductSummaryResponse>> response;
        if (search != null) {
            response = productService.advanceSearchWithSpecifications(pageable, search);
        } else if (categoryId != null && !categoryId.isBlank()) {
            response = productService.getAllProductsByCategory(pageable, categoryId);
        } else if (keyword != null && !keyword.isBlank()) {
            response = productService.searchProducts(pageable, keyword);
        } else {
            response = productService.getAllProducts(pageable);
        }

        return ApiResponse.<PageResponse<List<ProductSummaryResponse>>>builder()
                .message("Get products successfully")
                .data(response)
                .build();
    }

    @GetMapping({"/api/v1/products/{id}", "/api/v1/product/{id}"})
    public ApiResponse<ProductResponse> get(@PathVariable String id) {
        ProductResponse response = productService.getProductById(id);
        return ApiResponse.<ProductResponse>builder()
                .message("Get product by id successfully")
                .data(response)
                .build();
    }

    @GetMapping({"/api/v1/products/slug/{slug}", "/api/v1/product/slug/{slug}"})
    public ApiResponse<ProductResponse> getBySlug(@PathVariable String slug) {
        return ApiResponse.<ProductResponse>builder()
                .message("Get product by slug successfully")
                .data(productService.getProductBySlug(slug))
                .build();
    }

    @GetMapping({
            "/api/v1/products/variants/{variantId}",
            "/api/v1/product/variants/{variantId}",
            "/api/v1/products/variants/{variantId}/snapshot",
            "/api/v1/product/variants/{variantId}/snapshot"
    })
    public ApiResponse<ProductVariantSnapshotResponse> getVariantSnapshot(@PathVariable String variantId) {
        return ApiResponse.<ProductVariantSnapshotResponse>builder()
                .message("Get product variant snapshot successfully")
                .data(productService.getProductVariantSnapshot(variantId))
                .build();
    }

    @GetMapping({"/api/v1/products/{id}/variants", "/api/v1/product/{id}/variants"})
    public ApiResponse<List<ProductVariantResponse>> getVariants(@PathVariable String id) {
        return ApiResponse.<List<ProductVariantResponse>>builder()
                .message("Get product variants successfully")
                .data(productService.getProductVariants(id))
                .build();
    }

    @GetMapping({"/api/v1/products/{id}/size-chart", "/api/v1/product/{id}/size-chart"})
    public ApiResponse<SizeChartResponse> getSizeChart(@PathVariable String id) {
        ProductResponse product = productService.getProductById(id);
        return ApiResponse.<SizeChartResponse>builder()
                .message("Get product size chart successfully")
                .data(sizeChartService.getById(product.getSizeChartId()))
                .build();
    }

    @GetMapping({"/api/v1/products/all", "/api/v1/product/all"})
    public ApiResponse<PageResponse<List<ProductSummaryResponse>>> getAll(
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PageResponse<List<ProductSummaryResponse>> response = productService.getAllProducts(pageable);
        return ApiResponse.<PageResponse<List<ProductSummaryResponse>>>builder()
                .message("Get all product successfully")
                .data(response)
                .build();

    }

    @GetMapping({"/api/v1/products/search", "/api/v1/product/search"})
    public ApiResponse<PageResponse<List<ProductSummaryResponse>>> searchByKeyword(
            @RequestParam String keyword,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PageResponse<List<ProductSummaryResponse>> response = productService.searchProducts(pageable, keyword);
        return ApiResponse.<PageResponse<List<ProductSummaryResponse>>>builder()
                .message("Search product by keyword successfully")
                .data(response)
                .build();

    }

    @GetMapping({"/api/v1/products/category/{categoryId}", "/api/v1/product/category/{categoryId}"})
    public ApiResponse<PageResponse<List<ProductSummaryResponse>>> getAllByCategory(
            @PathVariable String categoryId,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PageResponse<List<ProductSummaryResponse>> response = productService.getAllProductsByCategory(pageable, categoryId);
        return ApiResponse.<PageResponse<List<ProductSummaryResponse>>>builder()
                .message("Get all product by category successfully")
                .data(response)
                .build();

    }

    @GetMapping({"/api/v1/products/category-slug/{categorySlug}", "/api/v1/product/category-slug/{categorySlug}"})
    public ApiResponse<PageResponse<List<ProductSummaryResponse>>> getAllByCategorySlug(
            @PathVariable String categorySlug,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.<PageResponse<List<ProductSummaryResponse>>>builder()
                .message("Get products by category slug successfully")
                .data(productService.getAllProductsByCategorySlug(pageable, categorySlug))
                .build();
    }

    @GetMapping({"/api/v1/products/brand-slug/{brandSlug}", "/api/v1/product/brand-slug/{brandSlug}"})
    public ApiResponse<PageResponse<List<ProductSummaryResponse>>> getAllByBrandSlug(
            @PathVariable String brandSlug,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.<PageResponse<List<ProductSummaryResponse>>>builder()
                .message("Get products by brand slug successfully")
                .data(productService.getAllProductsByBrandSlug(pageable, brandSlug))
                .build();
    }

    @GetMapping({"/api/v1/products/advance-search", "/api/v1/product/advance-search"})
    public ApiResponse<PageResponse<List<ProductSummaryResponse>>> advanceSearch(
            @RequestParam(value = "search", required = false) String[] product,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PageResponse<List<ProductSummaryResponse>> response = productService.advanceSearchWithSpecifications(pageable, product);
        return ApiResponse.<PageResponse<List<ProductSummaryResponse>>>builder()
                .message("Search product successfully")
                .data(response)
                .build();

    }

    @GetMapping("/admin/products")
    public ApiResponse<PageResponse<List<ProductSummaryResponse>>> getAdminProducts(
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.<PageResponse<List<ProductSummaryResponse>>>builder()
                .message("Get products successfully")
                .data(productService.getAllProductsForBackoffice(pageable))
                .build();
    }

    @PostMapping("/admin/products")
    public ApiResponse<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.<ProductResponse>builder()
                .message("Create product successfully")
                .data(productService.createProduct(request))
                .build();
    }

    @GetMapping("/admin/products/{productId}")
    public ApiResponse<ProductResponse> getAdminProduct(@PathVariable String productId) {
        return ApiResponse.<ProductResponse>builder()
                .message("Get product by id successfully")
                .data(productService.getProductById(productId))
                .build();
    }

    @PutMapping("/admin/products/{productId}")
    public ApiResponse<ProductResponse> updateProduct(@PathVariable String productId,
                                                      @Valid @RequestBody ProductUpdateRequest request) {
        return ApiResponse.<ProductResponse>builder()
                .message("Update product successfully")
                .data(productService.updateProduct(productId, request))
                .build();
    }

    @PutMapping("/admin/products/{productId}/variants")
    public ApiResponse<ProductResponse> updateProductVariants(@PathVariable String productId,
                                                              @Valid @RequestBody ProductVariantBatchRequest request) {
        return ApiResponse.<ProductResponse>builder()
                .message("Update product variants successfully")
                .data(productService.updateProductVariants(productId, request))
                .build();
    }

    @PutMapping("/admin/products/{productId}/attributes")
    public ApiResponse<ProductResponse> updateProductAttributes(@PathVariable String productId,
                                                                @Valid @RequestBody AssignProductAttributeRequest request) {
        return ApiResponse.<ProductResponse>builder()
                .message("Update product attributes successfully")
                .data(productService.updateProductAttributes(productId, request))
                .build();
    }

    @PostMapping("/admin/products/{productId}/publish")
    public ApiResponse<ProductResponse> publishProduct(@PathVariable String productId) {
        return ApiResponse.<ProductResponse>builder()
                .message("Publish product successfully")
                .data(productService.publishProduct(productId))
                .build();
    }

    @PostMapping("/admin/products/{productId}/unpublish")
    public ApiResponse<ProductResponse> unpublishProduct(@PathVariable String productId) {
        return ApiResponse.<ProductResponse>builder()
                .message("Unpublish product successfully")
                .data(productService.unpublishProduct(productId))
                .build();
    }

    @PostMapping("/admin/products/{productId}/archive")
    public ApiResponse<ProductResponse> archiveProduct(@PathVariable String productId) {
        return ApiResponse.<ProductResponse>builder()
                .message("Archive product successfully")
                .data(productService.archiveProduct(productId))
                .build();
    }

    @DeleteMapping("/admin/products/{productId}")
    public ApiResponse<Void> deleteProduct(@PathVariable String productId) {
        productService.deleteProduct(productId);
        return ApiResponse.<Void>builder()
                .message("Delete product successfully")
                .build();
    }



}
