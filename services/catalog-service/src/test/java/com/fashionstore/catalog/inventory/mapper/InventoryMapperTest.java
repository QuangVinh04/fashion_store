package com.fashionstore.catalog.inventory.mapper;

import com.fashionstore.catalog.inventory.dto.InventoryResponse;
import com.fashionstore.catalog.inventory.entity.Inventory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryMapperTest {

    private final InventoryMapper mapper = new InventoryMapperImpl();

    @Test
    void mapsReservedAndAvailableQuantities() {
        Inventory inventory = Inventory.builder()
                .productId("product-1")
                .variantId("variant-1")
                .quantity(10)
                .reservedQuantity(3)
                .build();

        InventoryResponse response = mapper.toResponse(inventory);

        assertThat(response.getQuantity()).isEqualTo(10);
        assertThat(response.getQuantityReserved()).isEqualTo(3);
        assertThat(response.getQuantityAvailable()).isEqualTo(7);
    }
}
