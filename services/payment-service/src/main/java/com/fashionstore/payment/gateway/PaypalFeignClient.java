package com.fashionstore.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "paypal-api", url = "${payment.paypal.base-url}")
public interface PaypalFeignClient {

    @PostMapping(value = "/v1/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    JsonNode getAccessToken(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody MultiValueMap<String, String> form
    );

    @PostMapping(value = "/v2/checkout/orders", consumes = MediaType.APPLICATION_JSON_VALUE)
    JsonNode createOrder(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("PayPal-Request-Id") String requestId,
            @RequestBody Object body
    );

    @PostMapping(value = "/v2/checkout/orders/{orderId}/capture", consumes = MediaType.APPLICATION_JSON_VALUE)
    JsonNode captureOrder(
            @PathVariable String orderId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("PayPal-Request-Id") String requestId,
            @RequestBody Object body
    );
}
