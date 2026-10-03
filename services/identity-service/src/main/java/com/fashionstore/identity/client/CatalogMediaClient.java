package com.fashionstore.identity.client;

import com.fashionstore.common.config.feign.FeignGlobalConfig;
import com.fashionstore.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDateTime;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.exception.IdentityErrorCode;

@FeignClient(
        name = "catalog-media-client",
        url = "${app.catalog.base-url}",
        configuration = {FeignGlobalConfig.class, CatalogMediaClientConfig.class}
)
public interface CatalogMediaClient {

    @GetMapping("/internal/v1/media/{mediaId}")
    ApiResponse<MediaResponse> getById(@PathVariable("mediaId") String mediaId, @RequestParam("ownerId") String ownerId);

    record MediaResponse(String id, String ownerId, String purpose, String status, String url, LocalDateTime expiresAt) {
        public void validateTemporaryAvatar(String userId, String mediaId) {
            if (!java.util.Objects.equals(id, mediaId) || !java.util.Objects.equals(ownerId, userId)
                    || !"AVATAR".equals(purpose) || !"TEMP".equals(status) || url == null || url.isBlank()
                    || expiresAt == null || !expiresAt.isAfter(LocalDateTime.now()))
                throw new AppException(IdentityErrorCode.AVATAR_MEDIA_INVALID);
        }
    }
}
