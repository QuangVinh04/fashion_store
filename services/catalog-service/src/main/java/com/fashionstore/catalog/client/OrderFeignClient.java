package com.fashionstore.catalog.client;

import com.fashionstore.common.config.feign.FeignGlobalConfig;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.contracts.order.dto.VerifyPurchaseRequest;
import com.fashionstore.contracts.order.dto.VerifyPurchaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "order-service",
        url = "${app.clients.order-base-url}",
        configuration = {FeignGlobalConfig.class, OrderFeignClientConfig.class}
)
public interface OrderFeignClient {

    @PostMapping("/internal/v1/orders/verify-purchase")
    ApiResponse<VerifyPurchaseResponse> verifyPurchase(@RequestBody VerifyPurchaseRequest request);
}
