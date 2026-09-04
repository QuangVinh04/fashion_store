package com.fashionstore.inventory.controller;

import com.fashionstore.inventory.dto.inventory.*;
import com.fashionstore.inventory.service.InventoryService;
import com.fashionstore.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InventoryController {

    InventoryService inventoryService;

    @GetMapping("/variants/{variantId}")
    public ApiResponse<InventoryResponse> getByVariantId(
            @PathVariable String variantId) {
        return ApiResponse.<InventoryResponse>builder()
                .message("Get inventory successfully")
                .data(inventoryService.getByVariantId(variantId))
                .build();
    }

    @GetMapping("/variants/batch")
    public ApiResponse<List<InventoryResponse>> getByVariantIds(
            @RequestParam List<String> variantIds) {
        return ApiResponse.<List<InventoryResponse>>builder()
                .message("Get inventories successfully")
                .data(inventoryService.getByVariantIds(variantIds))
                .build();
    }

    // ── BUSINESS OPERATIONS ──────────────────────────────────────────

    /**
     * cart-service gọi endpoint này để check tồn kho.
     * Read-only, không có side effect.
     */
    @PostMapping("/check")
    public ApiResponse<CheckStockResponse> checkStock(
            @Valid @RequestBody CheckStockRequest request) {
        return ApiResponse.<CheckStockResponse>builder()
                .message("Stock checked")
                .data(inventoryService.checkStock(request))
                .build();
    }

    /**
     * order-service gọi khi tạo order để giữ hàng.
     * All-or-nothing: 1 item thiếu → toàn bộ fail.
     * Idempotent: cùng orderId gọi lại → không reserve 2 lần.
     */
    @PostMapping("/reserve")
    public ApiResponse<ReserveStockResponse> reserveStock(
            @Valid @RequestBody ReserveStockRequest request) {
        return ApiResponse.<ReserveStockResponse>builder()
                .message("Stock reserved successfully")
                .data(inventoryService.reserveStock(request))
                .build();
    }

    /**
     * order-service gọi khi cancel order / payment failed.
     * Trả hàng về available.
     * Idempotent: gọi 2 lần → chỉ release 1 lần.
     */
    @PostMapping("/release")
    public ApiResponse<Void> releaseStock(
            @Valid @RequestBody ReleaseStockRequest request) {
        inventoryService.releaseStock(request);
        return ApiResponse.<Void>builder()
                .message("Stock released successfully")
                .build();
    }

    /**
     * order-service gọi khi order CONFIRMED (đã thanh toán xong).
     * Xuất kho thực sự: giảm quantityOnHand.
     */
    @PostMapping("/confirm/{orderId}")
    public ApiResponse<Void> confirmStock(@PathVariable String orderId) {
        inventoryService.confirmStock(orderId);
        return ApiResponse.<Void>builder()
                .message("Stock confirmed successfully")
                .build();
    }

}
