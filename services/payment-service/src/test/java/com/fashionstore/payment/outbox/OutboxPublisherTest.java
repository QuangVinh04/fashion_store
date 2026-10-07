package com.fashionstore.payment.outbox;

import com.fashionstore.common.messaging.RabbitTopology;
import com.fashionstore.common.messaging.outbox.ConfirmedRabbitSender;
import com.fashionstore.common.messaging.outbox.OutboxEventStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    OutboxEventRepository repository;
    @Mock
    ConfirmedRabbitSender sender;
    @InjectMocks
    OutboxPublisher publisher;

    @Test
    void confirmedPublishMarksEventPublishedWithOutboxIdHeader() {
        OutboxEvent event = OutboxEvent.pending("order-1", "inventory.reserved", "{\"a\":1}");
        when(repository.findBatchToPublish(eq(OutboxEventStatus.PENDING), any())).thenReturn(List.of(event));

        publisher.publishPendingEvents();

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(eq(RabbitTopology.EXCHANGE), eq("inventory.reserved"), message.capture(), eq(event.getId()));
        assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}");
        assertThat((String) message.getValue().getMessageProperties().getHeader(RabbitTopology.OUTBOX_EVENT_ID_HEADER))
                .isEqualTo(event.getId());
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        verify(repository).save(event);
    }

    @Test
    void unconfirmedPublishStaysPendingWithBackoff() {
        OutboxEvent event = OutboxEvent.pending("order-1", "inventory.reserved", "{}");
        when(repository.findBatchToPublish(eq(OutboxEventStatus.PENDING), any())).thenReturn(List.of(event));
        doThrow(new AmqpException("broker nack")).when(sender).send(anyString(), anyString(), any(), anyString());

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("broker nack");
        assertThat(event.getNextAttemptAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void brokerOutageOfAboutAnHourDoesNotFailTheEventYet() {
        OutboxEvent event = OutboxEvent.pending("order-1", "inventory.reserved", "{}");
        for (int i = 0; i < OutboxPublisher.MAX_ATTEMPTS - 1; i++) {
            event.scheduleRetry("down", OutboxPublisher.MAX_ATTEMPTS);
        }
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);

        event.scheduleRetry("down", OutboxPublisher.MAX_ATTEMPTS);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(OutboxPublisher.MAX_ATTEMPTS).isGreaterThanOrEqualTo(20);
    }

    @Test
    void backoffGrowsButIsCappedAtFiveMinutes() {
        OutboxEvent event = OutboxEvent.pending("order-1", "inventory.reserved", "{}");
        for (int i = 0; i < 15; i++) {
            event.scheduleRetry("down", OutboxPublisher.MAX_ATTEMPTS);
        }
        assertThat(event.getNextAttemptAt()).isBetween(
                event.getUpdatedAt().plusSeconds(299), event.getUpdatedAt().plusSeconds(301));
    }

    @Test
    void fastPathSkipsRowAlreadyLockedByTheScanner() {
        when(repository.findByIdForPublish("event-1")).thenReturn(Optional.empty());

        publisher.handleOutboxCreated(new OutboxCreatedEvent("event-1"));

        verify(sender, never()).send(anyString(), anyString(), any(), anyString());
    }

    @Test
    void fastPathPublishesTheRowItLocked() {
        OutboxEvent event = OutboxEvent.pending("order-1", "inventory.reserved", "{}");
        when(repository.findByIdForPublish(event.getId())).thenReturn(Optional.of(event));

        publisher.handleOutboxCreated(new OutboxCreatedEvent(event.getId()));

        verify(sender).send(eq(RabbitTopology.EXCHANGE), eq("inventory.reserved"), any(), eq(event.getId()));
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    }
}
