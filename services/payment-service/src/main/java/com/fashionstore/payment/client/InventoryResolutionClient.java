package com.fashionstore.payment.client;
import com.fashionstore.common.config.feign.FeignGlobalConfig;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.contracts.inventory.event.InventoryDisposition;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="inventory-resolution",url="${app.clients.catalog-base-url:http://localhost:8087}",configuration={FeignGlobalConfig.class,ResolutionClientConfig.class})
public interface InventoryResolutionClient {
    @GetMapping("/internal/inventory/orders/{id}/disposition")
    ApiResponse<InventoryDisposition> get(@PathVariable String id);
}
