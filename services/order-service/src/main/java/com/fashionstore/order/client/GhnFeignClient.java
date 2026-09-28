package com.fashionstore.order.client;

import com.fashionstore.order.dto.ghn.GhnCreateOrderRequest;
import com.fashionstore.order.dto.ghn.GhnCreateOrderResponse;
import com.fashionstore.order.dto.ghn.GhnFeeRequest;
import com.fashionstore.order.dto.ghn.GhnFeeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "ghn-client",
        url = "${app.ghn.api-url:https://dev-online-gateway.ghn.vn/shiip/public-api}"
)
public interface GhnFeignClient {

    @GetMapping("/master-data/province")
    JsonNode provinces(@RequestHeader("Token") String token, @RequestHeader("ShopId") String shopId);

    @GetMapping("/master-data/district")
    JsonNode districts(@RequestHeader("Token") String token, @RequestHeader("ShopId") String shopId,
                       @RequestParam("province_id") int provinceId);

    @GetMapping("/master-data/ward")
    JsonNode wards(@RequestHeader("Token") String token, @RequestHeader("ShopId") String shopId,
                   @RequestParam("district_id") int districtId);

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
