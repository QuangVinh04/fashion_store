package com.fashionstore.inventory.dto.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CheckStockRequest {

    @NotEmpty(message = "items must not be empty")
    @Valid
    private List<StockItem> items;

    @Data
    public static class StockItem {

        @NotBlank(message = "variantId is required")
        private String variantId;

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        private Integer quantity;
    }
}
