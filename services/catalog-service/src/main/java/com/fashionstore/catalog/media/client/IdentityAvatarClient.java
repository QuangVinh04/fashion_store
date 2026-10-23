package com.fashionstore.catalog.media.client;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.resilience.UpstreamFailure;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Đọc avatar hiện hành từ identity: lời gọi đọc nên retry; identity chết thì 502 thay vì rơi xuống 500. */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityAvatarClient {

    private final IdentityAvatarFeignClient identityAvatarFeignClient;

    @CircuitBreaker(name = "identityService")
    @Retry(name = "identityService", fallbackMethod = "getAvatarReferenceFallback")
    public ApiResponse<AvatarReferenceResponse> getAvatarReference(String userId) {
        return identityAvatarFeignClient.getAvatarReference(userId);
    }

    private ApiResponse<AvatarReferenceResponse> getAvatarReferenceFallback(String userId, Exception ex) {
        log.warn("[IdentityAvatarClient] identity-service unavailable for userId={}: {}", userId, ex.getMessage());
        throw UpstreamFailure.translate(ex);
    }

    public record AvatarReferenceResponse(String avatarMediaId, Long avatarRevision) {}
}
