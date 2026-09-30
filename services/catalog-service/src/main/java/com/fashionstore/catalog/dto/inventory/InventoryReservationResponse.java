package com.fashionstore.catalog.dto.inventory;

import com.fashionstore.catalog.entity.enumeration.InventoryReservationStatus;
import java.time.LocalDateTime;

public record InventoryReservationResponse(
        String orderId,
        int quantity,
        InventoryReservationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
