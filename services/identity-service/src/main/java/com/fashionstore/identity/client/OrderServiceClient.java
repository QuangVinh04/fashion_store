package com.fashionstore.identity.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class OrderServiceClient {

    private final RestClient restClient;
    private final String internalSecretToken;

    public OrderServiceClient(
            @Value("${app.services.order-service-url:${ORDER_BASE_URL:http://order-service:8089}}") String orderServiceUrl,
            @Value("${app.internal.secret-token:fashion-store-internal-secret-token}") String internalSecretToken) {
        this.restClient = RestClient.builder().baseUrl(orderServiceUrl).build();
        this.internalSecretToken = internalSecretToken;
    }

    public boolean triggerCartMerge(String userId, String anonymousId) {
        try {
            restClient.post()
                    .uri("/internal/v1/cart/merge")
                    .header("X-Internal-Token", internalSecretToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("userId", userId, "anonymousId", anonymousId))
                    .retrieve()
                    .toBodilessEntity();
            log.info("[Identity] Successfully triggered cart merge for userId={}, anonymousId={}", userId, anonymousId);
            return true;
        } catch (Exception e) {
            log.warn("[Identity] Failed to trigger cart merge for userId={}, anonymousId={}: {}", userId, anonymousId, e.getMessage());
            return false;
        }
    }
}
