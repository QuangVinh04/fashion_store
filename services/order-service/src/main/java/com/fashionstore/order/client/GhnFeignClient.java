package com.fashionstore.order.client;

import com.fashionstore.order.dto.ghn.GhnCreateOrderRequest;
import com.fashionstore.order.dto.ghn.GhnCreateOrderResponse;
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

    @GetMapping("/v3/master-data/province/all")
    JsonNode newProvinces(@RequestHeader("Token") String token, @RequestHeader("ShopId") String shopId,
                          @RequestParam("offset") int offset, @RequestParam("limit") int limit);

    @GetMapping("/v3/master-data/ward/all-by-province-id")
    JsonNode newWards(@RequestHeader("Token") String token, @RequestHeader("ShopId") String shopId,
                      @RequestParam("province_id") int provinceId,
                      @RequestParam("offset") int offset, @RequestParam("limit") int limit);

    @PostMapping(value = "/v2/shipping-order/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    GhnCreateOrderResponse createOrder(
            @RequestHeader("Token") String token,
            @RequestHeader("ShopId") String shopId,
            @RequestBody GhnCreateOrderRequest request
    );

    @PostMapping(value = "/v2/shipping-order/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    GhnCreateOrderResponse previewOrder(@RequestHeader("Token") String token,
                                        @RequestHeader("ShopId") String shopId,
                                        @RequestBody GhnCreateOrderRequest request);
}
