package com.fashionstore.order.client;

import com.fashionstore.common.config.feign.FeignGlobalConfig;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.order.client.dto.CartServiceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "cart-service",
        url = "${app.clients.cart-base-url}",
        configuration = FeignGlobalConfig.class)
public interface CartFeignClient {

    @GetMapping("/api/v1/cart")
    ApiResponse<CartServiceResponse> getMyCart();
}
