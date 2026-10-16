package com.fashionstore.order.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fashionstore.common.resilience.UpstreamFailure;
import com.fashionstore.order.dto.GhnCreateOrderRequest;
import com.fashionstore.order.dto.GhnCreateOrderResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cửa duy nhất tới {@link GhnFeignClient}, mỗi method ứng một lời gọi GHN để circuit breaker thấy đúng lỗi
 * hạ tầng (người gọi như {@link GhnClient} tự bắt và quy đổi sau). Chỉ lời gọi đọc mới retry; tạo/hủy vận
 * đơn có tác dụng phụ bên GHN nên một lần duy nhất, kết quả không rõ thì để reconcile.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GhnApi {

    private final GhnFeignClient ghn;

    @CircuitBreaker(name = "ghnService")
    @Retry(name = "ghnService", fallbackMethod = "failed")
    public JsonNode getOrderDetail(String token, String shopId, String orderCode) {
        return ghn.getOrderDetail(token, shopId, orderCode);
    }

    @CircuitBreaker(name = "ghnService")
    @Retry(name = "ghnService", fallbackMethod = "failed")
    public JsonNode newProvinces(String token, String shopId, int offset, int limit) {
        return ghn.newProvinces(token, shopId, offset, limit);
    }

    @CircuitBreaker(name = "ghnService")
    @Retry(name = "ghnService", fallbackMethod = "failed")
    public JsonNode newWards(String token, String shopId, int provinceId, int offset, int limit) {
        return ghn.newWards(token, shopId, provinceId, offset, limit);
    }

    @CircuitBreaker(name = "ghnService")
    @Retry(name = "ghnService", fallbackMethod = "failed")
    public GhnCreateOrderResponse previewOrder(String token, String shopId, GhnCreateOrderRequest request) {
        return ghn.previewOrder(token, shopId, request);
    }

    @CircuitBreaker(name = "ghnService", fallbackMethod = "failed")
    public GhnCreateOrderResponse createOrder(String token, String shopId, GhnCreateOrderRequest request) {
        return ghn.createOrder(token, shopId, request);
    }

    @CircuitBreaker(name = "ghnService", fallbackMethod = "failed")
    public JsonNode cancelOrder(String token, String shopId, Map<String, Object> request) {
        return ghn.cancelOrder(token, shopId, request);
    }

    // Một fallback cho mọi method: Resilience4j chọn theo kiểu tham số cuối (Exception) và kiểu trả về.
    private JsonNode failed(String token, String shopId, String orderCode, Exception ex) {
        throw translated(ex);
    }

    private JsonNode failed(String token, String shopId, int offset, int limit, Exception ex) {
        throw translated(ex);
    }

    private JsonNode failed(String token, String shopId, int provinceId, int offset, int limit, Exception ex) {
        throw translated(ex);
    }

    private GhnCreateOrderResponse failed(String token, String shopId, GhnCreateOrderRequest request, Exception ex) {
        throw translated(ex);
    }

    private JsonNode failed(String token, String shopId, Map<String, Object> request, Exception ex) {
        throw translated(ex);
    }

    private RuntimeException translated(Exception ex) {
        log.warn("[GHN] call failed: {}", ex.getMessage());
        return UpstreamFailure.translate(ex);
    }
}
