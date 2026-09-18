package com.fashionstore.catalog.client;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.contracts.order.dto.VerifyPurchaseRequest;
import com.fashionstore.contracts.order.dto.VerifyPurchaseResponse;
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

    public VerifyPurchaseResponse verifyPurchase(VerifyPurchaseRequest request) {
        try {
            ApiResponse<VerifyPurchaseResponse> response = orderFeignClient.verifyPurchase(request);
            if (response != null && response.getData() != null) {
                return response.getData();
            }
            return new VerifyPurchaseResponse(false, null);
        } catch (Exception e) {
            log.error("[OrderClient] Failed to verify purchase with order-service for userId={}: {}",
                    request.userId(), e.getMessage(), e);
            throw new com.fashionstore.common.exception.AppException(com.fashionstore.common.exception.ErrorCode.UPSTREAM_SERVICE_ERROR);
        }
    }
}
