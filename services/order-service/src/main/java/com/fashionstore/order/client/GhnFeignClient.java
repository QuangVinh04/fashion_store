package com.fashionstore.order.client;

import com.fashionstore.order.dto.ghn.GhnCreateOrderRequest;
import com.fashionstore.order.dto.ghn.GhnCreateOrderResponse;
import com.fashionstore.order.dto.ghn.GhnFeeRequest;
import com.fashionstore.order.dto.ghn.GhnFeeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "ghn-client",
        url = "${app.ghn.api-url:https://dev-online-gateway.ghn.vn/shiip/public-api}"
)
public interface GhnFeignClient {

    @PostMapping(value = "/v2/shipping-order/fee", consumes = MediaType.APPLICATION_JSON_VALUE)
    GhnFeeResponse calculateFee(
            @RequestHeader("Token") String token,
            @RequestHeader(value = "ShopId", required = false) String shopId,
            @RequestBody GhnFeeRequest request
    );

    @PostMapping(value = "/v2/shipping-order/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    GhnCreateOrderResponse createOrder(
            @RequestHeader("Token") String token,
            @RequestHeader("ShopId") String shopId,
            @RequestBody GhnCreateOrderRequest request
    );
}
