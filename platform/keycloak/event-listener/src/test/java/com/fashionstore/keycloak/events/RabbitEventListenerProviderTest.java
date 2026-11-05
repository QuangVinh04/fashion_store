package com.fashionstore.keycloak.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fashionstore.contracts.common.EventTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.Config;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakTransaction;
import org.keycloak.models.KeycloakTransactionManager;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class RabbitEventListenerProviderTest {

    @Mock
    KeycloakSession session;

    @Mock
    KeycloakTransactionManager transactions;

    @Mock
    RabbitEventPublisher publisher;

    @Mock
    RealmProvider realms;

    @Mock
    UserProvider users;

    @Mock
    RealmModel realm;

    @Mock
    UserModel user;

    @Captor
    ArgumentCaptor<KeycloakTransaction> enlisted;

    @Captor
    ArgumentCaptor<OutgoingEvent> published;

    RabbitEventListenerProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RabbitEventListenerProvider(session, publisher);
    }

    @Test
    void registrationIsPublishedOnlyAfterKeycloakCommitsTheUser() {
        when(session.getTransactionManager()).thenReturn(transactions);

        provider.onEvent(register());

        verify(publisher, never()).publishAsync(any());
        verify(transactions).enlistAfterCompletion(enlisted.capture());
        // Transaction manager của Keycloak begin() transaction rồi mới commit khi request kết thúc
        enlisted.getValue().begin();
        enlisted.getValue().commit();
        verify(publisher).publishAsync(published.capture());
        assertThat(published.getValue().routingKey()).isEqualTo(EventTypes.KEYCLOAK_USER_REGISTERED);
    }

    @Test
    void aRolledBackRegistrationIsNeverPublished() {
        when(session.getTransactionManager()).thenReturn(transactions);

        provider.onEvent(register());
        verify(transactions).enlistAfterCompletion(enlisted.capture());
        enlisted.getValue().begin();
        enlisted.getValue().rollback();

        verifyNoInteractions(publisher);
    }

    @Test
    void anAdminEditingAUserIsPublishedWithTheUsersCurrentEmail() {
        when(session.getTransactionManager()).thenReturn(transactions);
        stubUser("kc-1", "current@fashion.local");

        provider.onEvent(adminEvent(ResourceType.USER, OperationType.UPDATE, "users/kc-1"), false);
        verify(transactions).enlistAfterCompletion(enlisted.capture());
        enlisted.getValue().begin();
        enlisted.getValue().commit();

        verify(publisher).publishAsync(published.capture());
        assertThat(published.getValue().routingKey()).isEqualTo(EventTypes.KEYCLOAK_USER_EMAIL_UPDATED);
        assertThat(new String(published.getValue().body(), StandardCharsets.UTF_8)).contains("current@fashion.local");
    }

    @Test
    void adminEventsThatAreNotAUserUpdateAreIgnored() {
        provider.onEvent(adminEvent(ResourceType.USER, OperationType.DELETE, "users/kc-1"), false);
        provider.onEvent(adminEvent(ResourceType.GROUP_MEMBERSHIP, OperationType.CREATE, "users/kc-1/groups/g-1"), false);
        provider.onEvent(adminEvent(ResourceType.CLIENT, OperationType.UPDATE, "clients/c-1"), false);

        verifyNoInteractions(transactions, publisher);
    }

    @Test
    void anAdminEventForAUserThatNoLongerExistsPublishesNothing() {
        when(session.realms()).thenReturn(realms);
        when(session.users()).thenReturn(users);
        when(realms.getRealm("realm-1")).thenReturn(realm);
        when(users.getUserById(realm, "kc-1")).thenReturn(null);

        provider.onEvent(adminEvent(ResourceType.USER, OperationType.UPDATE, "users/kc-1"), false);

        verifyNoInteractions(transactions, publisher);
    }

    @Test
    void aUserProfileUpdateDoesNotCarryAnEmailSoItIsNotPublished() {
        Event update = new Event();
        update.setType(EventType.UPDATE_PROFILE);
        update.setUserId("kc-1");

        provider.onEvent(update);

        verifyNoInteractions(transactions, publisher);
    }

    @Test
    void eventTypesTheSystemDoesNotUseTouchNothing() {
        Event login = new Event();
        login.setType(EventType.LOGIN);
        login.setUserId("kc-1");

        provider.onEvent(login);

        verifyNoInteractions(transactions, publisher);
    }

    @Test
    void theFactoryUsesDevelopmentDefaultsWhenNothingIsConfigured() {
        Config.Scope scope = mock(Config.Scope.class);
        when(scope.get(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        when(scope.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        RabbitEventListenerProviderFactory factory = new RabbitEventListenerProviderFactory();

        factory.init(scope);
        EventListenerProvider created = factory.create(session);

        assertThat(factory.getId()).isEqualTo("fashion-rabbit");
        assertThat(created).isInstanceOf(RabbitEventListenerProvider.class);
        factory.close();
    }

    private static AdminEvent adminEvent(ResourceType type, OperationType operation, String path) {
        AdminEvent event = new AdminEvent();
        event.setRealmId("realm-1");
        event.setResourceType(type);
        event.setOperationType(operation);
        event.setResourcePath(path);
        return event;
    }

    private void stubUser(String userId, String email) {
        when(session.realms()).thenReturn(realms);
        when(session.users()).thenReturn(users);
        when(realms.getRealm("realm-1")).thenReturn(realm);
        when(users.getUserById(realm, userId)).thenReturn(user);
        when(user.getEmail()).thenReturn(email);
    }

    private static Event register() {
        Event event = new Event();
        event.setType(EventType.REGISTER);
        event.setUserId("kc-1");
        event.setDetails(Map.of("email", "an@fashion.local", "first_name", "An", "last_name", "Nguyen"));
        return event;
    }
}
