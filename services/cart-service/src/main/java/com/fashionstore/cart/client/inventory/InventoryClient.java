package com.fashionstore.cart.client.inventory;

import com.fashionstore.cart.dto.inventory.InventoryDto;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.cart.exception.CartErrorCode;
import feign.FeignException;
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
public class InventoryClient {

    InventoryFeignClient inventoryFeignClient;

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getInventoryBatchFallback")
    @Retry(name = "inventoryService")
    public InventoryDto getInventoryBatch(String variantId) {
        return inventoryFeignClient.getInventoryBatch(variantId);
    }

    // Fallback: trả về unavailable cho tất cả → cart vẫn hiển thị được
    private InventoryDto getInventoryBatchFallback(
            String variantId, Exception ex) {
        log.warn("[CircuitBreaker] inventory-service unavailable for {} variants: {}",
                variantId, ex.getMessage());
        return InventoryDto.unavailable(variantId);
    }
}
