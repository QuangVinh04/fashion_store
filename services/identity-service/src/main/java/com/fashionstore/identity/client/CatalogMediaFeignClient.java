package com.fashionstore.identity.client;

import com.fashionstore.common.config.feign.FeignGlobalConfig;
import com.fashionstore.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "catalog-media-client",
        url = "${app.catalog.base-url}",
        configuration = {FeignGlobalConfig.class, CatalogMediaClientConfig.class}
)
public interface CatalogMediaFeignClient {

    @GetMapping("/internal/v1/media/{mediaId}")
    ApiResponse<CatalogMediaClient.MediaResponse> getById(@PathVariable("mediaId") String mediaId, @RequestParam("ownerId") String ownerId);
}
