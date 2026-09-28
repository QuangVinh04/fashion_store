package com.fashionstore.order.client;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
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
    /** Địa chỉ không thuộc {@code userId} cũng là 404 → ADDRESS_NOT_FOUND. */
    public UserAddressDto getAddress(String userId, String addressId) {
        try {
            ApiResponse<UserAddressDto> response = identityFeignClient.getAddressOfUser(userId, addressId);
            return response != null ? response.getData() : null;
        } catch (FeignException.NotFound e) {
            log.warn("Address not found in identity-service: {}", addressId);
            throw new AppException(OrderErrorCode.ADDRESS_NOT_FOUND);
        } catch (Exception e) {
            // Sự cố hạ tầng, không phải lỗi của khách — 502 như catalog, không để rơi xuống 500
            log.error("Failed to fetch address from identity-service: {}", addressId, e);
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        }
    }

    public InternalUserDto getUser(String userId) {
        try {
            ApiResponse<InternalUserDto> response = identityFeignClient.getUserById(userId);
            return response != null ? response.getData() : null;
        } catch (FeignException.NotFound e) {
            log.warn("[IdentityClient] User not found: {}", userId);
            return null;
        } catch (Exception e) {
            log.warn("[IdentityClient] Failed to fetch user from identity-service: {}", e.getMessage());
            return null;
        }
    }
}
