package com.fashionstore.catalog.client;

import com.fashionstore.common.dto.ApiResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityUserClient {

    private final IdentityUserFeignClient identityUserFeignClient;

    @CircuitBreaker(name = "identityService", fallbackMethod = "getUserByIdFallback")
    public Optional<UserInfoResponse> getUserById(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        ApiResponse<UserInfoResponse> res = identityUserFeignClient.getUserById(userId);
        if (res != null && res.getData() != null) {
            return Optional.of(res.getData());
        }
        return Optional.empty();
    }

    public Optional<UserInfoResponse> getUserByIdFallback(String userId, Throwable ex) {
        log.warn("[IdentityUserClient] CircuitBreaker fallback for user {}: {}", userId, ex.getMessage());
        return Optional.empty();
    }

    public record UserInfoResponse(
            String id,
            String email,
            String fullName,
            String phone
    ) {}
}
