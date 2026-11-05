package com.fashionstore.keycloak.events;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Một instance cho mỗi request của Keycloak (nhẹ: chỉ giữ session + publisher dùng chung).
 * Event chỉ được gửi SAU khi transaction của request commit, để không phát "user đã đăng ký"
 * cho một user mà Keycloak rồi rollback.
 */
final class RabbitEventListenerProvider implements EventListenerProvider {

    private final KeycloakSession session;
    private final RabbitEventPublisher publisher;

    RabbitEventListenerProvider(KeycloakSession session, RabbitEventPublisher publisher) {
        this.session = session;
        this.publisher = publisher;
    }

    @Override
    public void onEvent(Event event) {
        KeycloakUserEventMapper.map(event).ifPresent(this::publishAfterCommit);
    }

    // Realm đặt registrationEmailAsUsername và không cho user tự sửa username nên email chỉ đổi khi admin sửa user.
    // Admin event không mang email: đọc email hiện tại từ user
    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        if (event.getResourceType() != ResourceType.USER || event.getOperationType() != OperationType.UPDATE) {
            return;
        }
        String userId = userIdOf(event.getResourcePath());
        if (userId == null) {
            return;
        }
        RealmModel realm = session.realms().getRealm(event.getRealmId());
        UserModel user = realm == null ? null : session.users().getUserById(realm, userId);
        if (user != null) {
            KeycloakUserEventMapper.userEmailChanged(userId, user.getEmail()).ifPresent(this::publishAfterCommit);
        }
    }

    // resourcePath của sửa user là "users/{id}"; "users/{id}/groups/..." thuộc resource type khác nên không tới đây
    private static String userIdOf(String resourcePath) {
        if (resourcePath == null || !resourcePath.startsWith("users/")) {
            return null;
        }
        String id = resourcePath.substring("users/".length());
        return id.isBlank() || id.contains("/") ? null : id;
    }

    private void publishAfterCommit(OutgoingEvent outgoing) {
        session.getTransactionManager().enlistAfterCompletion(new AbstractKeycloakTransaction() {
            @Override
            protected void commitImpl() {
                publisher.publishAsync(outgoing);
            }

            @Override
            protected void rollbackImpl() {
                // Request bị rollback: thay đổi không xảy ra, không có gì để phát
            }
        });
    }

    @Override
    public void close() {
        // Không giữ tài nguyên theo request; publisher dùng chung do factory đóng
    }
}
