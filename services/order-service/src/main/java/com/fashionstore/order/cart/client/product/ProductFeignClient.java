package com.fashionstore.order.cart.client.product;

import com.fashionstore.order.cart.dto.product.ProductVariantDto;
import com.fashionstore.common.config.feign.FeignGlobalConfig;
import com.fashionstore.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "product-service",
        url = "${app.clients.catalog-base-url}",
        configuration = FeignGlobalConfig.class
)
public interface ProductFeignClient {

    @GetMapping("/api/v1/products/variants/{id}")
    ApiResponse<ProductVariantDto> getVariant(@PathVariable("id") String variantId);

    @GetMapping("/api/v1/products/variants/batch")
    ApiResponse<List<ProductVariantDto>> getVariantsBatch(@RequestParam List<String> variantIds);
}
