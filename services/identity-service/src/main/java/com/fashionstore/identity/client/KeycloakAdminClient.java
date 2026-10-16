package com.fashionstore.identity.client;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.resilience.UpstreamFailure;
import com.fashionstore.identity.dto.user.KeycloakUserRepresentation;
import com.fashionstore.identity.exception.IdentityErrorCode;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class KeycloakAdminClient {

    KeycloakAdminFeignClient keycloakAdminFeignClient;

    /** enabled=false: không đăng nhập / refresh token được nữa. Đặt cờ là idempotent nên retry được. */
    @CircuitBreaker(name = "keycloakAdmin")
    @Retry(name = "keycloakAdmin", fallbackMethod = "setEnabledFallback")
    public void setEnabled(String userId, boolean enabled) {
        call(userId, () -> keycloakAdminFeignClient.updateUser(userId, new KeycloakUserRepresentation(enabled)));
    }

    @CircuitBreaker(name = "keycloakAdmin")
    @Retry(name = "keycloakAdmin", fallbackMethod = "logoutFallback")
    public void logout(String userId) {
        call(userId, () -> keycloakAdminFeignClient.logout(userId));
    }

    private void call(String userId, Runnable request) {
        try {
            request.run();
        } catch (FeignException.NotFound e) {
            log.warn("[KeycloakAdmin] user {} not found in Keycloak", userId);
            throw new AppException(IdentityErrorCode.USER_NOT_FOUND);
        }
    }

    private void setEnabledFallback(String userId, boolean enabled, Exception ex) {
        throw failed(userId, ex);
    }

    private void logoutFallback(String userId, Exception ex) {
        throw failed(userId, ex);
    }

    private RuntimeException failed(String userId, Exception ex) {
        log.error("[KeycloakAdmin] request for user {} failed: {}", userId, ex.getMessage());
        return UpstreamFailure.translate(ex);
    }
}
