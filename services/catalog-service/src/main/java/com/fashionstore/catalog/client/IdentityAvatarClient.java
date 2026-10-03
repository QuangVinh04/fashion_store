package com.fashionstore.catalog.client;

import com.fashionstore.common.config.feign.FeignGlobalConfig;
import com.fashionstore.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "identity-avatar-client",
        url = "${app.clients.identity-base-url}",
        configuration = {FeignGlobalConfig.class, IdentityAvatarClientConfig.class}
)
public interface IdentityAvatarClient {

    @GetMapping("/internal/v1/users/{userId}/avatar-reference")
    ApiResponse<AvatarReferenceResponse> getAvatarReference(@PathVariable("userId") String userId);

    record AvatarReferenceResponse(
            String avatarMediaId,
            Long avatarRevision
    ) {}
}
