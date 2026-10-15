package com.fashionstore.notification.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.notification.InAppNotificationRequested;
import com.fashionstore.notification.repository.NotificationRepository;
import com.fashionstore.notification.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(NotificationServiceImpl.class)
class NotificationServiceTest {
    @Autowired NotificationService service;
    @Autowired NotificationRepository repository;
    @MockitoBean CurrentUserProvider currentUser;

    @BeforeEach void setUp() { when(currentUser.getCurrentUserId()).thenReturn("buyer-1"); }

    private void create(String user, String order) {
        service.create(new InAppNotificationRequested(user, "ORDER_CONFIRMED", "Đã xác nhận", "Đơn đã xác nhận.", order));
    }

    @Test void historyAndUnreadCountAreScopedToCurrentUser() {
        create("buyer-1", "order-1"); create("buyer-2", "order-2"); create("buyer-1", "order-3");
        var result = service.list(1, 1);
        assertThat(result.unreadCount()).isEqualTo(2);
        assertThat(result.page().getTotalPage()).isEqualTo(2);
        assertThat(result.page().getItems()).hasSize(1);
        assertThat(result.page().getItems().getFirst().orderId()).isEqualTo("order-3");
        assertThat(result.page().getItems().getFirst().createdAt()).isNotNull();
    }

    @Test void anotherUsersNotificationCannotBeMarkedRead() {
        create("buyer-2", "order-2");
        var id = repository.findAll().getFirst().getId();
        assertThatThrownBy(() -> service.markRead(id)).isInstanceOf(AppException.class);
        assertThat(repository.findById(id).orElseThrow().getReadAt()).isNull();
    }

    @Test void repeatedReadIsIdempotentAndReadAllLeavesOtherUsersUntouched() {
        create("buyer-1", "order-1"); create("buyer-1", "order-3"); create("buyer-2", "order-2");
        var id = service.list(1, 10).page().getItems().getFirst().id();
        service.markRead(id);
        var readAt = repository.findById(id).orElseThrow().getReadAt();
        service.markRead(id);
        assertThat(repository.findById(id).orElseThrow().getReadAt()).isEqualTo(readAt).isNotNull();
        assertThat(service.list(1, 10).unreadCount()).isEqualTo(1);
        service.markAllRead();
        assertThat(service.list(1, 10).unreadCount()).isZero();
        assertThat(repository.countByUserIdAndReadAtIsNull("buyer-2")).isEqualTo(1);
    }

    @Test void paginationAndAnonymousRecipientAreRejected() {
        assertThatThrownBy(() -> service.list(0, 10)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.list(1, 51)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> create("anon:checkout", "order-1")).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.count()).isZero();
    }

    @Test void replayedInAppMessageUsesProcessedMessageGuardAndCreatesOneNotification() {
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcOperations.class);
        var processed = new com.fashionstore.common.messaging.processed.ProcessedMessageService(com.fashionstore.common.redis.RedisService.disabled(), jdbc);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        org.mockito.Mockito.when(jdbc.update(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(1, 0);
        var listener = new com.fashionstore.notification.messaging.InAppNotificationListener(mapper, processed, service);
        var event = com.fashionstore.contracts.common.EventEnvelope.v1("notification.in-app.requested", "order-1", null,
                new InAppNotificationRequested("buyer-1", "ORDER_CONFIRMED", "Đã xác nhận", "Đơn của bạn", "order-1"));
        listener.handle(event, "message-1"); listener.handle(event, "message-1");
        assertThat(repository.count()).isEqualTo(1);
        assertThat(service.list(1, 10).unreadCount()).isEqualTo(1);
        assertThatThrownBy(() -> listener.handle(com.fashionstore.contracts.common.EventEnvelope.v1(
                "unsupported", "order-1", null, event.payload()), "message-2"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.count()).isEqualTo(1);
    }
}
