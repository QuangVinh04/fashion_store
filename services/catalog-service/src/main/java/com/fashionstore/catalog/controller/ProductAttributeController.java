package com.fashionstore.catalog.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.catalog.dto.ProductAttributeCreateRequest;
import com.fashionstore.catalog.dto.ProductAttributeOptionRequest;
import com.fashionstore.catalog.dto.ProductAttributeOptionResponse;
import com.fashionstore.catalog.dto.ProductAttributeResponse;
import com.fashionstore.catalog.dto.ProductAttributeUpdateRequest;
import com.fashionstore.catalog.service.ProductAtrributeService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductAttributeController {

    ProductAtrributeService productAttributeService;

    @GetMapping({"/api/v1/product-attributes", "/api/v1/product/attributes"})
    public ApiResponse<List<ProductAttributeResponse>> getPublishedAttributes() {
        return ApiResponse.<List<ProductAttributeResponse>>builder()
                .message("Get product attributes successfully")
                .data(productAttributeService.getAllPublishedAttributes())
                .build();
    }

    @GetMapping("/admin/product-attributes")
    public ApiResponse<PageResponse<List<ProductAttributeResponse>>> getAdminAttributes(
            @RequestParam(required = false) String keyword,
            @PageableDefault(page = 0, size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.<PageResponse<List<ProductAttributeResponse>>>builder()
                .message("Get product attributes successfully")
                .data(productAttributeService.getAllAttributes(pageable, keyword))
                .build();
    }

    @PostMapping("/admin/product-attributes")
    public ApiResponse<ProductAttributeResponse> createAttribute(
            @Valid @RequestBody ProductAttributeCreateRequest request) {
        return ApiResponse.<ProductAttributeResponse>builder()
                .message("Create product attribute successfully")
                .data(productAttributeService.createAttribute(request))
                .build();
    }

    @GetMapping("/admin/product-attributes/{attributeId}")
    public ApiResponse<ProductAttributeResponse> getAttribute(@PathVariable String attributeId) {
        return ApiResponse.<ProductAttributeResponse>builder()
                .message("Get product attribute successfully")
                .data(productAttributeService.getAttributeById(attributeId))
                .build();
    }

    @PutMapping("/admin/product-attributes/{attributeId}")
    public ApiResponse<ProductAttributeResponse> updateAttribute(
            @PathVariable String attributeId,
            @Valid @RequestBody ProductAttributeUpdateRequest request) {
        return ApiResponse.<ProductAttributeResponse>builder()
                .message("Update product attribute successfully")
                .data(productAttributeService.updateAttribute(attributeId, request))
                .build();
    }

    @DeleteMapping("/admin/product-attributes/{attributeId}")
    public ApiResponse<Void> deleteAttribute(@PathVariable String attributeId) {
        productAttributeService.deleteAttribute(attributeId);
        return ApiResponse.<Void>builder()
                .message("Delete product attribute successfully")
                .build();
    }

    @PostMapping("/admin/product-attributes/{attributeId}/options")
    public ApiResponse<ProductAttributeOptionResponse> addAttributeOption(
            @PathVariable String attributeId,
            @Valid @RequestBody ProductAttributeOptionRequest request) {
        return ApiResponse.<ProductAttributeOptionResponse>builder()
                .message("Create product attribute option successfully")
                .data(productAttributeService.addAttributeOption(attributeId, request))
                .build();
    }

    @PutMapping("/admin/product-attributes/{attributeId}/options/{optionId}")
    public ApiResponse<ProductAttributeOptionResponse> updateAttributeOption(
            @PathVariable String attributeId,
            @PathVariable String optionId,
            @Valid @RequestBody ProductAttributeOptionRequest request) {
        return ApiResponse.<ProductAttributeOptionResponse>builder()
                .message("Update product attribute option successfully")
                .data(productAttributeService.updateAttributeOption(attributeId, optionId, request))
                .build();
    }

    @DeleteMapping("/admin/product-attributes/{attributeId}/options/{optionId}")
    public ApiResponse<Void> deleteAttributeOption(
            @PathVariable String attributeId,
            @PathVariable String optionId) {
        productAttributeService.deleteAttributeOption(attributeId, optionId);
        return ApiResponse.<Void>builder()
                .message("Delete product attribute option successfully")
                .build();
    }
}
