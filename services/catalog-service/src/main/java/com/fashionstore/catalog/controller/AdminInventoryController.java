package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.inventory.InventoryLedgerResponse;
import com.fashionstore.catalog.dto.inventory.LowStockItemResponse;
import com.fashionstore.catalog.service.InventoryService;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDateTime;
import com.fashionstore.catalog.dto.InventoryResponse;
import com.fashionstore.catalog.dto.inventory.InventoryListItemResponse;
import com.fashionstore.catalog.dto.inventory.InventoryReservationResponse;
import com.fashionstore.catalog.dto.inventory.InventoryStockState;
import com.fashionstore.catalog.dto.inventory.ReceiveStockRequest;
import com.fashionstore.catalog.dto.inventory.UpdateThresholdRequest;
import com.fashionstore.catalog.entity.enumeration.InventoryLedgerType;
import com.fashionstore.catalog.entity.enumeration.InventoryReservationStatus;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
@RestController
@RequestMapping("/admin/inventory")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Admin - Inventory", description = "Quản lý tồn kho cho Quản trị viên (Admin)")
public class AdminInventoryController {

    InventoryService inventoryService;

    @GetMapping
    public ApiResponse<PageResponse<List<InventoryListItemResponse>>> list(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "ALL") InventoryStockState state,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @ParameterObject @PageableDefault(page = 0, size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.<PageResponse<List<InventoryListItemResponse>>>builder()
                .data(inventoryService.searchForAdmin(query, state, includeInactive, pageable)).build();
    }

    @PostMapping("/variants/{variantId}/receipts")
    public ApiResponse<InventoryResponse> receive(@PathVariable String variantId,
                                                   @Valid @RequestBody ReceiveStockRequest request) {
        return ApiResponse.<InventoryResponse>builder()
                .data(inventoryService.receiveStock(variantId, request)).build();
    }

    @PutMapping("/variants/{variantId}/threshold")
    public ApiResponse<InventoryResponse> threshold(@PathVariable String variantId,
                                                     @Valid @RequestBody UpdateThresholdRequest request) {
        return ApiResponse.<InventoryResponse>builder()
                .data(inventoryService.updateThreshold(variantId, request.getMinThreshold())).build();
    }

    @GetMapping("/variants/{variantId}/reservations")
    public ApiResponse<PageResponse<List<InventoryReservationResponse>>> reservations(
            @PathVariable String variantId,
            @RequestParam(defaultValue = "RESERVED") InventoryReservationStatus status,
            @ParameterObject @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.<PageResponse<List<InventoryReservationResponse>>>builder()
                .data(inventoryService.getReservations(variantId, status, pageable)).build();
    }

    @Operation(summary = "Lấy danh sách sản phẩm sắp hết hàng",
            description = "Trả về các sản phẩm/variant có số lượng khả dụng (quantity - reservedQuantity) nhỏ hơn hoặc bằng ngưỡng threshold.")
    @GetMapping("/low-stock")
    public ApiResponse<PageResponse<List<LowStockItemResponse>>> getLowStock(
            @RequestParam(required = false) Integer threshold,
            @ParameterObject
            @PageableDefault(page = 0, size = 20) Pageable pageable
    ) {
        return ApiResponse.<PageResponse<List<LowStockItemResponse>>>builder()
                .message("Get low stock inventory successfully")
                .data(inventoryService.getLowStock(threshold, pageable))
                .build();
    }

    @Operation(summary = "Đếm số lượng variant sắp hết hàng")
    @GetMapping("/low-stock/count")
    public ApiResponse<Long> countLowStock(@RequestParam(required = false) Integer threshold) {
        return ApiResponse.<Long>builder()
                .message("Count low stock inventory successfully")
                .data(inventoryService.countLowStock(threshold))
                .build();
    }

    @Operation(summary = "Lấy lịch sử biến động sổ cái kho (Ledger)",
            description = "Trả về lịch sử các giao dịch RESERVE, CONFIRM, RELEASE, RESTOCK, ADJUST.")
    @GetMapping("/ledger")
    public ApiResponse<PageResponse<List<InventoryLedgerResponse>>> getLedger(
            @RequestParam(required = false) String variantId,
            @RequestParam(required = false) InventoryLedgerType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toTime,
            @ParameterObject
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.<PageResponse<List<InventoryLedgerResponse>>>builder()
                .message("Get inventory ledger successfully")
                .data(inventoryService.getLedger(variantId, type, fromTime, toTime, pageable))
                .build();
    }
}
