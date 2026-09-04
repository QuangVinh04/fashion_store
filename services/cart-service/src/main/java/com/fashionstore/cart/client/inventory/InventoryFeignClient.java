package com.fashionstore.cart.client.inventory;

import com.fashionstore.cart.dto.inventory.StockCheckRequest;
import com.fashionstore.cart.dto.inventory.StockCheckResult;
import com.fashionstore.common.config.feign.FeignGlobalConfig;
import com.fashionstore.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "inventory-service",
        url = "${app.clients.inventory-base-url}",
        configuration = FeignGlobalConfig.class
)
public interface InventoryFeignClient {

    /** Read-only, không có side effect — đúng như inventory-service tài liệu hóa cho cart-service. */
    @PostMapping("/api/v1/inventory/check")
    ApiResponse<StockCheckResult> checkStock(@RequestBody StockCheckRequest request);
}
