package com.fashionstore.identity.client;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.identity.dto.user.KeycloakUserRepresentation;
import com.fashionstore.identity.exception.IdentityErrorCode;
import feign.FeignException;
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

    /** enabled=false: không đăng nhập / refresh token được nữa. */
    public void setEnabled(String userId, boolean enabled) {
        call(userId, () -> keycloakAdminFeignClient.updateUser(userId, new KeycloakUserRepresentation(enabled)));
    }

    public void logout(String userId) {
        call(userId, () -> keycloakAdminFeignClient.logout(userId));
    }

    private void call(String userId, Runnable request) {
        try {
            request.run();
        } catch (FeignException.NotFound e) {
            log.warn("[KeycloakAdmin] user {} not found in Keycloak", userId);
            throw new AppException(IdentityErrorCode.USER_NOT_FOUND);
        } catch (FeignException e) {
            log.error("[KeycloakAdmin] request for user {} failed: {}", userId, e.getMessage());
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        }
    }
}
