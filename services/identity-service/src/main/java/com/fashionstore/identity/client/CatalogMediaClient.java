package com.fashionstore.identity.client;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.resilience.UpstreamFailure;
import com.fashionstore.identity.exception.IdentityErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Đọc metadata media từ catalog: lời gọi đọc nên retry; catalog chết thì 502, không để rơi xuống 500. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogMediaClient {

    private final CatalogMediaFeignClient catalogMediaFeignClient;

    @CircuitBreaker(name = "catalogService")
    @Retry(name = "catalogService", fallbackMethod = "getByIdFallback")
    public ApiResponse<MediaResponse> getById(String mediaId, String ownerId) {
        return catalogMediaFeignClient.getById(mediaId, ownerId);
    }

    private ApiResponse<MediaResponse> getByIdFallback(String mediaId, String ownerId, Exception ex) {
        log.warn("[CatalogMediaClient] catalog-service unavailable for mediaId={}: {}", mediaId, ex.getMessage());
        throw UpstreamFailure.translate(ex);
    }

    public record MediaResponse(String id, String ownerId, String purpose, String status, String url, LocalDateTime expiresAt) {
        public void validateTemporaryAvatar(String userId, String mediaId) {
            if (!java.util.Objects.equals(id, mediaId) || !java.util.Objects.equals(ownerId, userId)
                    || !"AVATAR".equals(purpose) || !"TEMP".equals(status) || url == null || url.isBlank()
                    || expiresAt == null || !expiresAt.isAfter(LocalDateTime.now()))
                throw new AppException(IdentityErrorCode.AVATAR_MEDIA_INVALID);
        }
    }
}
