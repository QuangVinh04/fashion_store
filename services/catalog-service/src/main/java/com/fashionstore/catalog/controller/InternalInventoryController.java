package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.service.InventoryService;
import com.fashionstore.common.dto.ApiResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/inventory")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalInventoryController {

    InventoryService inventoryService;

    @GetMapping("/low-stock/count")
    public ApiResponse<Long> countLowStock(@RequestParam(defaultValue = "10") int threshold) {
        return ApiResponse.<Long>builder()
                .message("Count low stock inventory successfully")
                .data(inventoryService.countLowStock(threshold))
                .build();
    }
}
