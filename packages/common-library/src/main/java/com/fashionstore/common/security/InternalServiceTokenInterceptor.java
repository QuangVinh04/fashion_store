package com.fashionstore.common.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Gắn access token của service account (client_credentials, Keycloak) vào Feign client gọi /internal/**.
 * Chỉ khai báo trong configuration của Feign client nội bộ — không gắn cho client ra bên ngoài (GHN, VNPay...).
 */
public class InternalServiceTokenInterceptor implements RequestInterceptor {

    public static final String REGISTRATION_ID = "keycloak";

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public InternalServiceTokenInterceptor(OAuth2AuthorizedClientManager authorizedClientManager) {
        this.authorizedClientManager = authorizedClientManager;
    }

    @Override
    public void apply(RequestTemplate template) {
        // Principal cố định: token được cache trong AuthorizedClientService, tự lấy lại khi hết hạn
        OAuth2AuthorizedClient client = authorizedClientManager.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
                        .principal(REGISTRATION_ID)
                        .build());
        if (client == null) {
            throw new IllegalStateException("Cannot obtain service account token for internal call");
        }
        template.header(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
    }
}
