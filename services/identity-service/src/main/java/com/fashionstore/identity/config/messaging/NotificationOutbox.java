package com.fashionstore.identity.config.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.messaging.RabbitTopology;
import com.fashionstore.common.messaging.outbox.ConfirmedRabbitSender;
import com.fashionstore.common.messaging.outbox.OutboxEventStatus;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.identity.entity.OutboxEvent;
import com.fashionstore.identity.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Transactional outbox của identity-service.
 *
 * <p>{@link #record} chạy đồng bộ trong transaction của service phát event, nên dòng outbox và thay đổi
 * nghiệp vụ cùng commit hoặc cùng rollback. {@link #relay} định kỳ đẩy các dòng đã commit lên RabbitMQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutbox {

    /** 20 lần với backoff trần 5 phút ≈ 1 giờ chờ broker sống lại trước khi chuyển FAILED. */
    static final int MAX_ATTEMPTS = 20;

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final ConfirmedRabbitSender confirmedRabbitSender;

    @EventListener
    public void record(EventEnvelope<?> event) {
        if (!EventTypes.NOTIFICATION_EMAIL_REQUESTED.equals(event.eventType())
                && !EventTypes.PROFILE_AVATAR_CHANGED.equals(event.eventType())) {
            return;
        }
        try {
            repository.save(new OutboxEvent(event.eventType(), objectMapper.writeValueAsString(event)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize notification event", exception);
        }
    }

    /**
     * Mỗi dòng được gửi và cập nhật trạng thái độc lập: dòng lỗi chỉ bị lùi lịch, không làm rollback
     * (và gửi lại) những dòng đã gửi thành công trong cùng batch.
     */
    @Transactional
    @Scheduled(fixedDelayString = "${app.outbox.relay-delay-ms:3000}")
    public void relay() {
        repository.findBatchToPublish(OutboxEventStatus.PENDING, LocalDateTime.now()).forEach(this::publish);
    }

    private void publish(OutboxEvent event) {
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setHeader(RabbitTopology.OUTBOX_EVENT_ID_HEADER, event.getId());
            Message message = new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties);

            // Chỉ trả về khi broker đã ack và route được; mọi trường hợp khác ném exception.
            confirmedRabbitSender.send(RabbitTopology.EXCHANGE, event.getRoutingKey(), message, event.getId());
            event.markPublished();
        } catch (Exception exception) {
            event.scheduleRetry(exception.getMessage(), MAX_ATTEMPTS);
            if (event.getStatus() == OutboxEventStatus.FAILED) {
                log.error("Outbox event [ID: {}, routingKey: {}] FAILED sau {} lần gửi, cần xử lý tay: {}",
                        event.getId(), event.getRoutingKey(), event.getAttempts(), exception.getMessage(), exception);
            } else {
                log.warn("Gửi outbox event [ID: {}] thất bại lần {}, thử lại lúc {}: {}",
                        event.getId(), event.getAttempts(), event.getNextAttemptAt(), exception.getMessage());
            }
        }
        repository.save(event);
    }

    @Transactional
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldEvents() {
        repository.deleteByStatusAndPublishedAtBefore(OutboxEventStatus.PUBLISHED, LocalDateTime.now().minusDays(7));
    }
}
