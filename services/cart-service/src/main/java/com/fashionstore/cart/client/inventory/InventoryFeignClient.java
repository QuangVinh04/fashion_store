package com.fashionstore.cart.client.inventory;

import com.fashionstore.cart.dto.inventory.InventoryDto;
import com.fashionstore.common.config.feign.FeignGlobalConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "inventory-service",
        url = "${app.clients.inventory-base-url}",
        configuration = FeignGlobalConfig.class
)
public interface InventoryFeignClient {

    @GetMapping("/api/v1/inventory//variants/{variantId}")
    InventoryDto getInventoryBatch(@PathVariable String variantId);
}
