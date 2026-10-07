package com.fashionstore.identity.config.messaging;

import com.fashionstore.common.messaging.RabbitTopology;
import com.fashionstore.common.messaging.outbox.ConfirmedRabbitSender;
import com.fashionstore.common.messaging.outbox.OutboxEventStatus;
import com.fashionstore.identity.entity.OutboxEvent;
import com.fashionstore.identity.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxTest {

    @Mock
    OutboxEventRepository repository;
    @Mock
    ConfirmedRabbitSender sender;

    NotificationOutbox outbox;

    @BeforeEach
    void setUp() {
        outbox = new NotificationOutbox(repository, new ObjectMapper(), sender);
    }

    @Test
    void oneFailingEventDoesNotBlockOrResendTheOthers() {
        OutboxEvent broken = event("broken", "notification.email.requested");
        OutboxEvent healthy = event("healthy", "profile.avatar.changed");
        when(repository.findBatchToPublish(eq(OutboxEventStatus.PENDING), any())).thenReturn(List.of(broken, healthy));
        doThrow(new AmqpException("broker nack")).when(sender)
                .send(anyString(), eq("notification.email.requested"), any(), eq("broken"));

        outbox.relay();

        assertThat(broken.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(broken.getAttempts()).isEqualTo(1);
        assertThat(broken.getNextAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(broken.getPublishedAt()).isNull();

        assertThat(healthy.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(healthy.getPublishedAt()).isNotNull();
        verify(sender).send(eq(RabbitTopology.EXCHANGE), eq("profile.avatar.changed"), any(), eq("healthy"));
    }

    @Test
    void eventFailsPermanentlyAfterMaxAttemptsInsteadOfBlockingTheQueueHead() {
        OutboxEvent event = event("broken", "notification.email.requested");
        for (int i = 0; i < NotificationOutbox.MAX_ATTEMPTS - 1; i++) {
            event.scheduleRetry("down", NotificationOutbox.MAX_ATTEMPTS);
        }
        when(repository.findBatchToPublish(eq(OutboxEventStatus.PENDING), any())).thenReturn(List.of(event));
        doThrow(new AmqpException("down")).when(sender).send(anyString(), anyString(), any(), anyString());

        outbox.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
    }

    private static OutboxEvent event(String id, String routingKey) {
        OutboxEvent event = new OutboxEvent(routingKey, "{}");
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
