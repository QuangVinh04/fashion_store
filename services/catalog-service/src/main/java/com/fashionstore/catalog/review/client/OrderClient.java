package com.fashionstore.catalog.review.client;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.resilience.UpstreamFailure;
import com.fashionstore.contracts.order.dto.VerifyPurchaseRequest;
import com.fashionstore.contracts.order.dto.VerifyPurchaseResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderClient {

    OrderFeignClient orderFeignClient;

    /** POST nhưng chỉ đọc (kiểm tra đã mua chưa) nên retry được; order chết thì 502, không được trả "chưa mua". */
    @CircuitBreaker(name = "orderService")
    @Retry(name = "orderService", fallbackMethod = "verifyPurchaseFallback")
    public VerifyPurchaseResponse verifyPurchase(VerifyPurchaseRequest request) {
        ApiResponse<VerifyPurchaseResponse> response = orderFeignClient.verifyPurchase(request);
        if (response != null && response.getData() != null) {
            return response.getData();
        }
        return new VerifyPurchaseResponse(false, null);
    }

    private VerifyPurchaseResponse verifyPurchaseFallback(VerifyPurchaseRequest request, Exception ex) {
        log.error("[OrderClient] Failed to verify purchase with order-service for userId={}: {}",
                request.userId(), ex.getMessage());
        throw UpstreamFailure.translate(ex);
    }
}
