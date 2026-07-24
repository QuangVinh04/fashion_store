package com.fashionstore.order.client;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.order.client.dto.CartServiceResponse;
import feign.FeignException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartServiceClient {

    CartFeignClient cartFeignClient;

    public CartServiceResponse getMyCart() {
        try {
            ApiResponse<CartServiceResponse> response = cartFeignClient.getMyCart();
            if (response == null || response.getData() == null) {
                throw new AppException(com.fashionstore.order.config.ErrorCode.CART_EMPTY);
            }
            return response.getData();
        } catch (FeignException exception) {
            if (exception.status() == com.fashionstore.order.config.ErrorCode.CART_EMPTY.getStatusCode().value()) {
                throw new AppException(com.fashionstore.order.config.ErrorCode.CART_EMPTY);
            }
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR, exception);
        }
    }
}
