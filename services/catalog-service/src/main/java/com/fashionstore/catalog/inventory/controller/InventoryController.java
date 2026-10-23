package com.fashionstore.catalog.inventory.controller;

import com.fashionstore.catalog.inventory.dto.CheckStockRequest;
import com.fashionstore.catalog.inventory.dto.CheckStockResponse;
import com.fashionstore.catalog.inventory.dto.InventoryResponse;
import com.fashionstore.catalog.inventory.dto.ReleaseStockRequest;
import com.fashionstore.catalog.inventory.dto.ReserveStockRequest;
import com.fashionstore.catalog.inventory.dto.ReserveStockResponse;
import com.fashionstore.catalog.inventory.dto.UpdateStockRequest;
import com.fashionstore.catalog.inventory.service.InventoryService;
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

    @PutMapping("/variants/{variantId}")
    public ApiResponse<InventoryResponse> updateStock(
            @PathVariable String variantId,
            @Valid @RequestBody UpdateStockRequest request) {
        return ApiResponse.<InventoryResponse>builder()
                .message("Stock updated successfully")
                .data(inventoryService.updateStock(variantId, request))
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

    /** Legacy bulk return restock is retired; administrators use audited manual stock operations. */
    @PostMapping("/restock/{orderId}")
    public ApiResponse<Void> restock(@PathVariable String orderId) {
        throw new com.fashionstore.common.exception.AppException(com.fashionstore.catalog.inventory.exception.InventoryErrorCode.LEGACY_RESTOCK_DISABLED);
    }

    @GetMapping("/low-stock")
    public ApiResponse<com.fashionstore.common.dto.PageResponse<List<com.fashionstore.catalog.inventory.dto.LowStockItemResponse>>> getLowStock(
            @RequestParam(required = false) Integer threshold,
            @org.springdoc.core.annotations.ParameterObject
            @org.springframework.data.web.PageableDefault(page = 0, size = 20) org.springframework.data.domain.Pageable pageable
    ) {
        return ApiResponse.<com.fashionstore.common.dto.PageResponse<List<com.fashionstore.catalog.inventory.dto.LowStockItemResponse>>>builder()
                .message("Get low stock inventory successfully")
                .data(inventoryService.getLowStock(threshold, pageable))
                .build();
    }

    @GetMapping("/low-stock/count")
    public ApiResponse<Long> countLowStock(@RequestParam(required = false) Integer threshold) {
        return ApiResponse.<Long>builder()
                .message("Count low stock inventory successfully")
                .data(inventoryService.countLowStock(threshold))
                .build();
    }

    @GetMapping("/ledger")
    public ApiResponse<com.fashionstore.common.dto.PageResponse<List<com.fashionstore.catalog.inventory.dto.InventoryLedgerResponse>>> getLedger(
            @RequestParam(required = false) String variantId,
            @RequestParam(required = false) com.fashionstore.catalog.inventory.entity.InventoryLedgerType type,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime fromTime,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime toTime,
            @org.springdoc.core.annotations.ParameterObject
            @org.springframework.data.web.PageableDefault(page = 0, size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) org.springframework.data.domain.Pageable pageable
    ) {
        return ApiResponse.<com.fashionstore.common.dto.PageResponse<List<com.fashionstore.catalog.inventory.dto.InventoryLedgerResponse>>>builder()
                .message("Get inventory ledger successfully")
                .data(inventoryService.getLedger(variantId, type, fromTime, toTime, pageable))
                .build();
    }
}
