package com.fashionstore.inventory.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InventoryResponse {

    private String        id;
    private String        variantId;
    private String        productId;
    private int           quantity;
    private int           quantityReserved;
    private int           quantityAvailable;
    private LocalDateTime updatedAt;
}