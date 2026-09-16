package com.fashionstore.order.client;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.order.config.GhnProperties;
import com.fashionstore.order.dto.UserAddressDto;
import com.fashionstore.order.dto.ghn.GhnCreateOrderRequest;
import com.fashionstore.order.dto.ghn.GhnCreateOrderResponse;
import com.fashionstore.order.dto.ghn.GhnFeeRequest;
import com.fashionstore.order.dto.ghn.GhnFeeResponse;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderItem;
import com.fashionstore.order.entity.enumeration.ShippingMethod;
import com.fashionstore.order.exception.OrderErrorCode;
import feign.FeignException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GhnClient {

    GhnFeignClient ghnFeignClient;
    GhnProperties ghnProperties;

    /**
     * Tính phí vận chuyển qua GHN API.
     * Nếu token không cấu hình hoặc disable:
     * - allowMock == true: fallback phí ước tính chuẩn (dev/test offline).
     * - allowMock == false: ném UPSTREAM_SERVICE_ERROR (502).
     */
    public BigDecimal calculateFee(Integer districtId, String wardCode, int weightGram, ShippingMethod shippingMethod) {
        if (districtId == null || wardCode == null || wardCode.trim().isEmpty()) {
            throw new AppException(OrderErrorCode.SHIPPING_ADDRESS_INVALID);
        }

        int effectiveWeight = weightGram <= 0 ? 200 : weightGram;
        int serviceTypeId = (shippingMethod == ShippingMethod.EXPRESS) ? 1 : 2;

        String token = ghnProperties.getToken();
        boolean isMockAllowed = Boolean.TRUE.equals(ghnProperties.getAllowMock());
        boolean isGhnConfigured = Boolean.TRUE.equals(ghnProperties.getEnabled()) && token != null && !token.isBlank();

        if (!isGhnConfigured) {
            if (isMockAllowed) {
                log.info("[GHN] Token not configured or disabled; using standard estimation fallback (mock allowed)");
                return estimateFeeFallback(effectiveWeight, shippingMethod);
            }
            log.error("[GHN] GHN integration is not configured/disabled and mock is not allowed");
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        }

        GhnFeeRequest request = GhnFeeRequest.builder()
                .fromDistrictId(ghnProperties.getFromDistrictId())
                .fromWardCode(ghnProperties.getFromWardCode())
                .toDistrictId(districtId)
                .toWardCode(wardCode)
                .serviceTypeId(serviceTypeId)
                .weight(effectiveWeight)
                .length(20)
                .width(20)
                .height(10)
                .build();

        try {
            GhnFeeResponse response = ghnFeignClient.calculateFee(token, ghnProperties.getShopId(), request);
            if (response != null && response.getData() != null && response.getData().getTotal() != null) {
                return response.getData().getTotal();
            }
            log.warn("[GHN] Calculate fee response empty: {}", response);
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        } catch (FeignException e) {
            log.error("[GHN] Feign error while calculating fee: status={}, message={}", e.status(), e.getMessage());
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("[GHN] Unexpected error while calculating fee: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        }
    }

    /**
     * Tạo vận đơn bên GHN.
     */
    public String createOrder(Order order, UserAddressDto address, int weightGram) {
        if (address == null || address.getDistrictId() == null || address.getWardCode() == null || address.getWardCode().trim().isEmpty()) {
            throw new AppException(OrderErrorCode.SHIPPING_ADDRESS_INVALID);
        }

        int effectiveWeight = weightGram <= 0 ? 200 : weightGram;
        String token = ghnProperties.getToken();
        boolean isMockAllowed = Boolean.TRUE.equals(ghnProperties.getAllowMock());
        boolean isGhnConfigured = Boolean.TRUE.equals(ghnProperties.getEnabled()) && token != null && !token.isBlank();

        if (!isGhnConfigured) {
            if (isMockAllowed) {
                String mockTracking = "GHN_MOCK_" + order.getOrderCode();
                log.info("[GHN] Token not configured or disabled; returning mock tracking code: {} (mock allowed)", mockTracking);
                return mockTracking;
            }
            log.error("[GHN] GHN integration is not configured/disabled and mock is not allowed");
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        }

        List<GhnCreateOrderRequest.GhnItem> items = order.getItems().stream()
                .map(item -> GhnCreateOrderRequest.GhnItem.builder()
                        .name(item.getProductName())
                        .code(item.getVariantId())
                        .quantity(item.getQuantity())
                        .price(item.getUnitPrice())
                        .weight(effectiveWeight / Math.max(1, order.getItems().size()))
                        .build())
                .toList();

        BigDecimal codAmount = order.getPaymentMethod() == PaymentMethod.COD ? order.getTotalAmount() : BigDecimal.ZERO;

        GhnCreateOrderRequest request = GhnCreateOrderRequest.builder()
                .paymentTypeId(1) // Shop trả cước vận chuyển
                .clientOrderCode(order.getOrderCode())
                .toName(order.getRecipientName())
                .toPhone(order.getRecipientPhone())
                .toAddress(order.getShippingAddress())
                .toDistrictId(address.getDistrictId())
                .toWardCode(address.getWardCode())
                .codAmount(codAmount)
                .weight(effectiveWeight)
                .length(20)
                .width(20)
                .height(10)
                .serviceTypeId(2)
                .items(items)
                .build();

        try {
            GhnCreateOrderResponse response = ghnFeignClient.createOrder(token, ghnProperties.getShopId(), request);
            if (response != null && response.getData() != null && response.getData().getOrderCode() != null) {
                return response.getData().getOrderCode();
            }
            log.warn("[GHN] Create order response empty: {}", response);
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        } catch (FeignException e) {
            log.error("[GHN] Feign error while creating order: status={}, message={}", e.status(), e.getMessage());
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("[GHN] Unexpected error while creating order: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        }
    }

    private BigDecimal estimateFeeFallback(int weightGram, ShippingMethod shippingMethod) {
        BigDecimal base = (shippingMethod == ShippingMethod.EXPRESS)
                ? BigDecimal.valueOf(40000)
                : BigDecimal.valueOf(25000);
        if (weightGram > 1000) {
            int extraKg = (weightGram - 1000 + 999) / 1000;
            base = base.add(BigDecimal.valueOf(extraKg * 5000L));
        }
        return base;
    }
}

