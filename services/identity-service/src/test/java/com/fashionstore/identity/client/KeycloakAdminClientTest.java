package com.fashionstore.identity.client;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.identity.dto.user.KeycloakUserRepresentation;
import com.fashionstore.identity.exception.IdentityErrorCode;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KeycloakAdminClientTest {

    @Mock
    KeycloakAdminFeignClient keycloakAdminFeignClient;

    KeycloakAdminClient keycloakAdminClient;

    @BeforeEach
    void setUp() {
        keycloakAdminClient = new KeycloakAdminClient(keycloakAdminFeignClient);
    }

    @Test
    void setEnabled_sendsOnlyTheEnabledFlag() {
        keycloakAdminClient.setEnabled("kc-1", false);

        ArgumentCaptor<KeycloakUserRepresentation> body = ArgumentCaptor.forClass(KeycloakUserRepresentation.class);
        verify(keycloakAdminFeignClient).updateUser(eq("kc-1"), body.capture());
        assertThat(body.getValue().enabled()).isFalse();
    }

    @Test
    void logout_endsAllSessionsOfTheUser() {
        keycloakAdminClient.logout("kc-1");

        verify(keycloakAdminFeignClient).logout("kc-1");
    }

    @Test
    void whenUserIsUnknownToKeycloak_throwsUserNotFound() {
        doThrow(new FeignException.NotFound("Not Found", request(), null, null))
                .when(keycloakAdminFeignClient).updateUser(eq("kc-x"), any());

        assertThatThrownBy(() -> keycloakAdminClient.setEnabled("kc-x", false))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(IdentityErrorCode.USER_NOT_FOUND);
    }

    @Test
    void whenKeycloakFails_throwsUpstreamServiceError() {
        doThrow(new FeignException.ServiceUnavailable("Service Unavailable", request(), null, null))
                .when(keycloakAdminFeignClient).logout("kc-1");

        assertThatThrownBy(() -> keycloakAdminClient.logout("kc-1"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR);
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.PUT, "/users/kc-1",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
    }
}
