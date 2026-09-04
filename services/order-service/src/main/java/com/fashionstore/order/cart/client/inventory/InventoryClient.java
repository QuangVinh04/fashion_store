package com.fashionstore.order.cart.client.inventory;

import com.fashionstore.order.cart.dto.inventory.StockCheckItem;
import com.fashionstore.order.cart.dto.inventory.StockCheckRequest;
import com.fashionstore.order.cart.dto.inventory.StockCheckResult;
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

    /**
     * Nhận list thay vì (variantId, quantity) đơn lẻ: annotation Resilience4j chỉ áp dụng được qua proxy
     * Spring AOP, tự gọi lại chính mình trong cùng class sẽ bỏ qua @CircuitBreaker/@Retry — nên không có
     * overload tiện lợi nào gọi vòng lại method này, gọi thẳng {@code checkStock(List.of(...))} ở call site.
     */
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "checkStockFallback")
    @Retry(name = "inventoryService")
    public StockCheckResult checkStock(List<StockCheckItem> items) {
        return inventoryFeignClient.checkStock(StockCheckRequest.builder().items(items).build()).getData();
    }

    // Fallback: coi như hết hàng thay vì để lộ lỗi hạ tầng ra khách hàng — cart vẫn hiển thị được,
    // chỉ không thêm/sửa số lượng được cho tới khi inventory-service hồi phục.
    private StockCheckResult checkStockFallback(List<StockCheckItem> items, Exception ex) {
        log.warn("[CircuitBreaker] inventory-service unavailable for {} variant(s): {}",
                items.size(), ex.getMessage());
        return StockCheckResult.unavailable(items);
    }
}
