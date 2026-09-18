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
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/inventory")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Admin - Inventory", description = "Quản lý tồn kho cho Quản trị viên (Admin)")
public class AdminInventoryController {

    InventoryService inventoryService;

    @Operation(summary = "Lấy danh sách sản phẩm sắp hết hàng",
            description = "Trả về các sản phẩm/variant có số lượng khả dụng (quantity - reservedQuantity) nhỏ hơn hoặc bằng ngưỡng threshold.")
    @GetMapping("/low-stock")
    public ApiResponse<PageResponse<List<LowStockItemResponse>>> getLowStock(
            @RequestParam(defaultValue = "10") int threshold,
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
    public ApiResponse<Long> countLowStock(@RequestParam(defaultValue = "10") int threshold) {
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
            @ParameterObject
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.<PageResponse<List<InventoryLedgerResponse>>>builder()
                .message("Get inventory ledger successfully")
                .data(inventoryService.getLedger(variantId, pageable))
                .build();
    }
}
