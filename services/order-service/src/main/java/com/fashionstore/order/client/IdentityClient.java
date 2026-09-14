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
    public UserAddressDto getAddress(String addressId) {
        try {
            ApiResponse<UserAddressDto> response = identityFeignClient.getAddressById(addressId);
            return response != null ? response.getData() : null;
        } catch (FeignException.NotFound e) {
            log.warn("Address not found in identity-service: {}", addressId);
            throw new AppException(OrderErrorCode.ADDRESS_NOT_FOUND);
        } catch (Exception e) {
            log.error("Failed to fetch address from identity-service: {}", addressId, e);
            throw e;
        }
    }
}
