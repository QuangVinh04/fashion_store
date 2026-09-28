package com.fashionstore.order.client;

import com.fashionstore.common.config.feign.FeignGlobalConfig;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.order.dto.ProductVariantDto;
import com.fashionstore.order.dto.StockCheckRequest;
import com.fashionstore.order.dto.StockCheckResult;
import com.fashionstore.order.dto.UserAddressDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@FeignClient(
        name = "identity-service",
        url = "${app.clients.identity-base-url:http://localhost:8082}",
        configuration = {FeignGlobalConfig.class, IdentityFeignClientConfig.class}
)
public interface IdentityFeignClient {
    @GetMapping("/internal/v1/users/{userId}/addresses/{addressId}")
    ApiResponse<UserAddressDto> getAddressOfUser(@PathVariable("userId") String userId,
                                                 @PathVariable("addressId") String addressId);

    @GetMapping("/internal/v1/users/{id}")
    ApiResponse<com.fashionstore.order.dto.InternalUserDto> getUserById(@PathVariable("id") String id);
}
