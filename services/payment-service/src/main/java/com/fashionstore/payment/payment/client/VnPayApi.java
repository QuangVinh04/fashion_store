package com.fashionstore.payment.payment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.payment.payment.exception.PaymentErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cửa tới {@link VnPayFeignClient}. QueryDr chỉ đọc trạng thái giao dịch nên retry được; VNPay hỏng thì
 * ngắt mạch và báo {@code PAYMENT_PROVIDER_ERROR} như trước, reconciler sẽ hỏi lại ở vòng sau.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VnPayApi {

    private final VnPayFeignClient vnPayFeignClient;

    @CircuitBreaker(name = "vnpayService")
    @Retry(name = "vnpayService", fallbackMethod = "queryFallback")
    public JsonNode query(Map<String, String> request) {
        return vnPayFeignClient.query(request);
    }

    private JsonNode queryFallback(Map<String, String> request, Exception ex) {
        log.warn("[VNPay] QueryDr failed: {}", ex.getMessage());
        throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR, ex);
    }
}
