package com.fashionstore.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/** HTTP client for VNPay query and refund operations. */
@FeignClient(name = "vnpay-api", url = "${payment.vnpay.api-url}")
public interface VnPayFeignClient {

    /** Sends a signed refund request to VNPay. */
    @PostMapping(value = "/merchant_webapi/api/transaction", consumes = MediaType.APPLICATION_JSON_VALUE)
    JsonNode refund(@RequestBody Map<String, String> request);
}
