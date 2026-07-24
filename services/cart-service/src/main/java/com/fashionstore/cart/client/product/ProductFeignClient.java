package com.fashionstore.cart.client.product;

import com.fashionstore.cart.dto.product.ProductVariantDto;
import com.fashionstore.common.config.feign.FeignGlobalConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "product-service",
        url = "${app.clients.product-base-url}",
        configuration = FeignGlobalConfig.class
)
public interface ProductFeignClient {

    @GetMapping("/api/v1/products/variants/{id}")
    ProductVariantDto getVariant(@PathVariable("id") String variantId);


    @PostMapping("/api/v1/products/variants/batch")
    List<ProductVariantDto> getVariantsBatch(@RequestBody List<String> variantIds);
}
