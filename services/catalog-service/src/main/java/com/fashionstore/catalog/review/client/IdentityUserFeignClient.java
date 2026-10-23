package com.fashionstore.catalog.review.client;

import com.fashionstore.common.config.feign.FeignGlobalConfig;
import com.fashionstore.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "identity-user-client",
        url = "${app.clients.identity-base-url}",
        configuration = {FeignGlobalConfig.class}
)
public interface IdentityUserFeignClient {

    @GetMapping("/internal/v1/users/{userId}")
    ApiResponse<IdentityUserClient.UserInfoResponse> getUserById(@PathVariable("userId") String userId);
}
