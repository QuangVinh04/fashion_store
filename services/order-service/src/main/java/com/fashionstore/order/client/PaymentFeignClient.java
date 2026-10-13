package com.fashionstore.order.client;

import com.fashionstore.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

@FeignClient(name = "payment-service", url = "${app.clients.payment-base-url:http://localhost:8085}",
        configuration = PaymentFeignClientConfig.class)
public interface PaymentFeignClient {

    record RefundWalletRequest(BigDecimal amount, String returnId, String orderId, String reason, String reasonType) {
        public RefundWalletRequest(BigDecimal amount, String returnId, String orderId, String reason) {
            this(amount, returnId, orderId, reason, "RETURN_REFUND");
        }
    }
    record RefundWalletResponse(String ledgerEntryId, String userId, long amount, long newBalance) {}

    @PostMapping("/internal/wallets/{userId}/refund")
    ApiResponse<RefundWalletResponse> refundToWallet(
            @PathVariable("userId") String userId,
            @RequestBody RefundWalletRequest request
    );
}
