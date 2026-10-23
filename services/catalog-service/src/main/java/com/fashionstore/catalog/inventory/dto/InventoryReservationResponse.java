package com.fashionstore.catalog.inventory.dto;

import com.fashionstore.catalog.inventory.entity.InventoryReservationStatus;
import java.time.LocalDateTime;

public record InventoryReservationResponse(
        String orderId,
        int quantity,
        InventoryReservationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
