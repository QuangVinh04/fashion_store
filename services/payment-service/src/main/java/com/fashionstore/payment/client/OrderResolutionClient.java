package com.fashionstore.payment.client;
import com.fashionstore.common.config.feign.FeignGlobalConfig;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.contracts.order.dto.OrderFundsDisposition;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="order-resolution",url="${app.clients.order-base-url:http://localhost:8089}",configuration={FeignGlobalConfig.class,ResolutionClientConfig.class})
public interface OrderResolutionClient {
    @GetMapping("/internal/v1/orders/{id}/funds-disposition")
    ApiResponse<OrderFundsDisposition> get(@PathVariable String id);
}
