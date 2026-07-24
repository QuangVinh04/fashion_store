package com.fashionstore.cart.dto.inventory;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDto {
    private String  variantId;
    private int     quantityOnHand;
    private int     quantityReserved;
    private int     quantityAvailable;  // onHand - reserved

    public boolean hasEnoughStock(int requestedQty) {
        return quantityAvailable >= requestedQty;
    }

    // Factory: dùng trong fallback khi inventory-service down
    public static InventoryDto unavailable(String variantId) {
        return new InventoryDto(variantId, 0, 0, 0);
    }
}
