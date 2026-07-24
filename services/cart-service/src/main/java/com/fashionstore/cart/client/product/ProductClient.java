package com.fashionstore.cart.client.product;

import com.fashionstore.cart.dto.product.ProductVariantDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductClient {

    ProductFeignClient productFeignClient;


    @CircuitBreaker(name = "product-service", fallbackMethod = "getVariantFallback")
    @Retry(name = "productService")
    public ProductVariantDto getVariant(String variantId) {
        return productFeignClient.getVariant(variantId);
    }

    @CircuitBreaker(name = "product-service", fallbackMethod = "getVariantsBatchFallback")
    @Retry(name = "productService")
    public List<ProductVariantDto> getVariantsBatch(List<String> variantIds) {
        return productFeignClient.getVariantsBatch(variantIds);
    }


    private ProductVariantDto getVariantFallback(String variantId, Exception ex) {
        log.warn("[CircuitBreaker] product-service unavailable for variantId={}: {}",
                variantId, ex.getMessage());
        return ProductVariantDto.unavailable(variantId);
    }

    private List<ProductVariantDto> getVariantsBatchFallback(
            List<String> variantIds, Exception ex) {
        log.warn("[CircuitBreaker] product-service unavailable for batch size={}: {}",
                variantIds.size(), ex.getMessage());
        return variantIds.stream()
                .map(ProductVariantDto::unavailable)
                .toList();
    }
}
