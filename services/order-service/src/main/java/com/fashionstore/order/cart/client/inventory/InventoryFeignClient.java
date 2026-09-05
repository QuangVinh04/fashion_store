package com.fashionstore.order.cart.client.inventory;

import com.fashionstore.order.cart.dto.inventory.StockCheckRequest;
import com.fashionstore.order.cart.dto.inventory.StockCheckResult;
import com.fashionstore.common.config.feign.FeignGlobalConfig;
import com.fashionstore.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "inventory-service",
        url = "${app.clients.catalog-base-url}",
        configuration = FeignGlobalConfig.class
)
public interface InventoryFeignClient {

    /** Read-only, không có side effect — đúng như catalog-service tài liệu hóa cho phần giỏ hàng. */
    @PostMapping("/api/v1/inventory/check")
    ApiResponse<StockCheckResult> checkStock(@RequestBody StockCheckRequest request);
}
