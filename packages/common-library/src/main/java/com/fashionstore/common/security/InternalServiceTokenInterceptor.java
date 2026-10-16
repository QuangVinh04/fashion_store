package com.fashionstore.common.security;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Gắn access token của service account (client_credentials, Keycloak) vào Feign client gọi /internal/**.
 * Chỉ khai báo trong configuration của Feign client nội bộ — không gắn cho client ra bên ngoài (GHN, VNPay...).
 */
@Slf4j
public class InternalServiceTokenInterceptor implements RequestInterceptor {

    public static final String REGISTRATION_ID = "keycloak";

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public InternalServiceTokenInterceptor(OAuth2AuthorizedClientManager authorizedClientManager) {
        this.authorizedClientManager = authorizedClientManager;
    }

    @Override
    public void apply(RequestTemplate template) {
        // Principal cố định: token được cache trong AuthorizedClientService, tự lấy lại khi hết hạn
        // Không lấy được token (Keycloak chết, từ chối client…) là sự cố hạ tầng: 502, không phải 500.
        OAuth2AuthorizedClient client;
        try {
            client = authorizedClientManager.authorize(
                    OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
                            .principal(REGISTRATION_ID)
                            .build());
        } catch (RuntimeException failure) {
            log.warn("Cannot obtain service account token for an internal call: {}", failure.getMessage());
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR, failure);
        }
        if (client == null || client.getAccessToken() == null) {
            log.warn("Cannot obtain service account token for an internal call: no authorized client");
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        }
        template.header(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
    }
}
