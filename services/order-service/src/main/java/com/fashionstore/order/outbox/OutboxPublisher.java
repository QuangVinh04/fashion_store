package com.fashionstore.order.outbox;


import com.fashionstore.common.messaging.outbox.OutboxEventStatus;
import com.fashionstore.order.config.messaging.RabbitMQNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private static final int MAX_ATTEMPTS = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    /**
     * Đường nhanh: gửi ngay sau khi transaction nghiệp vụ commit.
     */
    @Async
    // AFTER_COMMIT nghia la transaction nghiep vu da dong, nen viec cap nhat trang thai
    // outbox phai chay trong transaction moi. Spring tu choi @Transactional mac dinh o day.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOutboxCreated(OutboxCreatedEvent springEvent) {
        outboxEventRepository.findById(springEvent.outboxEventId())
                .ifPresent(this::publish);
    }

    /**
     * Lưới an toàn: quét những message chưa gửi được (app crash, Rabbit lag).
     */
    @Transactional
    @Scheduled(fixedDelayString = "${app.outbox.publisher-delay-ms:15000}")
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findBatchToPublish(
                OutboxEventStatus.PENDING,
                LocalDateTime.now()
        );
        pending.forEach(this::publish);
    }

    private void publish(OutboxEvent event) {
        if (OutboxEventStatus.PUBLISHED.equals(event.getStatus())) {
            return;
        }

        try {
            Message message = MessageBuilder
                    .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setHeader(RabbitMQNames.OUTBOX_EVENT_ID_HEADER, event.getId())
                    .build();

            rabbitTemplate.send(RabbitMQNames.EXCHANGE, event.getRoutingKey(), message);

            event.markPublished();
        } catch (Exception ex) {
            log.error("Failed to publish outbox event [ID: {}]: {}", event.getId(), ex.getMessage(), ex);
            event.scheduleRetry(ex.getMessage(), MAX_ATTEMPTS);
        }
        outboxEventRepository.save(event);
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        outboxEventRepository.deleteByStatusAndPublishedAtBefore(OutboxEventStatus.PUBLISHED, cutoff);
    }
}
