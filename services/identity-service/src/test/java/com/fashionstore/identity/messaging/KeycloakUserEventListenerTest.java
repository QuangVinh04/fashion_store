package com.fashionstore.identity.messaging;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.identity.event.KeycloakUserEvent;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.service.UserProvisioningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class KeycloakUserEventListenerTest {

    @Mock
    UserProvisioningService provisioningService;

    KeycloakUserEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new KeycloakUserEventListener(provisioningService, new ObjectMapper());
    }

    @Test
    void registeredUserIsProvisionedWithTheFullNameAndAnUnverifiedEmail() {
        listener.onKeycloakUserEvent(envelope(EventTypes.KEYCLOAK_USER_REGISTERED,
                new KeycloakUserEvent("kc-1", "an@fashion.local", "An", "Nguyen")));

        verify(provisioningService).provision("kc-1", "an@fashion.local", "An Nguyen", false);
    }

    @Test
    void registeredUserWithOnlyAFirstNameKeepsJustThatName() {
        listener.onKeycloakUserEvent(envelope(EventTypes.KEYCLOAK_USER_REGISTERED,
                new KeycloakUserEvent("kc-1", "an@fashion.local", "An", null)));

        verify(provisioningService).provision("kc-1", "an@fashion.local", "An", false);
    }

    @Test
    void emailUpdateReachesProvisioningWithoutAName() {
        listener.onKeycloakUserEvent(envelope(EventTypes.KEYCLOAK_USER_EMAIL_UPDATED,
                new KeycloakUserEvent("kc-1", "new@fashion.local", null, null)));

        verify(provisioningService).provision("kc-1", "new@fashion.local", null, false);
    }

    @Test
    void unknownEventTypeIsDeadLetteredInsteadOfRetriedForever() {
        assertThatThrownBy(() -> listener.onKeycloakUserEvent(envelope("keycloak.user.deleted",
                new KeycloakUserEvent("kc-1", "an@fashion.local", null, null))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(provisioningService);
    }

    @Test
    void emailAlreadyOwnedByAnotherUserIsAPermanentFailureSoItGoesToTheDeadLetterQueue() {
        doThrow(new AppException(IdentityErrorCode.ACCOUNT_LINK_CONFLICT))
                .when(provisioningService).provision("kc-1", "legacy@fashion.local", "An", false);

        assertThatThrownBy(() -> listener.onKeycloakUserEvent(envelope(EventTypes.KEYCLOAK_USER_REGISTERED,
                new KeycloakUserEvent("kc-1", "legacy@fashion.local", "An", null))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasCauseInstanceOf(AppException.class);
    }

    private static EventEnvelope<Object> envelope(String eventType, KeycloakUserEvent payload) {
        return EventEnvelope.v1(eventType, payload.userId(), "corr-1", payload);
    }
}
