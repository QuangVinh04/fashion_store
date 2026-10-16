package com.fashionstore.order.client;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.resilience.UpstreamFailure;
import com.fashionstore.order.dto.*;
import com.fashionstore.order.exception.OrderErrorCode;
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
public class IdentityClient {
    IdentityFeignClient identityFeignClient;
    /**
     * Địa chỉ không thuộc {@code userId} cũng là 404 → ADDRESS_NOT_FOUND. Sự cố hạ tầng không bị nuốt ở đây để
     * circuit breaker thấy, fallback mới đổi thành 502 (không để rơi xuống 500).
     */
    @CircuitBreaker(name = "identityService")
    @Retry(name = "identityService", fallbackMethod = "getAddressFallback")
    public UserAddressDto getAddress(String userId, String addressId) {
        try {
            ApiResponse<UserAddressDto> response = identityFeignClient.getAddressOfUser(userId, addressId);
            return response != null ? response.getData() : null;
        } catch (FeignException.NotFound e) {
            log.warn("Address not found in identity-service: {}", addressId);
            throw new AppException(OrderErrorCode.ADDRESS_NOT_FOUND);
        }
    }

    private UserAddressDto getAddressFallback(String userId, String addressId, Exception ex) {
        log.error("Failed to fetch address from identity-service: {}", addressId, ex);
        throw UpstreamFailure.translate(ex);
    }

    /** Thông tin phụ (thông báo): identity không trả lời được thì coi như không có. */
    @CircuitBreaker(name = "identityService")
    @Retry(name = "identityService", fallbackMethod = "getUserFallback")
    public InternalUserDto getUser(String userId) {
        try {
            ApiResponse<InternalUserDto> response = identityFeignClient.getUserById(userId);
            return response != null ? response.getData() : null;
        } catch (FeignException.NotFound e) {
            log.warn("[IdentityClient] User not found: {}", userId);
            return null;
        }
    }

    private InternalUserDto getUserFallback(String userId, Exception ex) {
        log.warn("[IdentityClient] Failed to fetch user from identity-service: {}", ex.getMessage());
        return null;
    }
}
