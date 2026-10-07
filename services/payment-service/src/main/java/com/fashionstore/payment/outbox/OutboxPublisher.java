package com.fashionstore.payment.outbox;

import com.fashionstore.common.messaging.RabbitTopology;
import com.fashionstore.common.messaging.outbox.ConfirmedRabbitSender;
import com.fashionstore.common.messaging.outbox.OutboxEventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
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

/**
 * Relay của transactional outbox: đọc dòng {@code outbox_event} đang chờ và đẩy lên RabbitMQ.
 *
 * <p>Hai đường cùng dẫn tới {@link #publish}:
 * <ul>
 *   <li><b>Đường nhanh</b> — ngay sau khi transaction nghiệp vụ commit, gửi luôn dòng vừa ghi.</li>
 *   <li><b>Lưới an toàn</b> — định kỳ quét các dòng còn PENDING (app crash trước khi gửi, broker sập...).</li>
 * </ul>
 * Cả hai đều khoá dòng bằng {@code FOR UPDATE SKIP LOCKED}: dòng nào đang được luồng khác gửi thì bỏ qua,
 * nên dù chạy nhiều instance cũng không gửi trùng do tranh nhau.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    /**
     * Backoff 2s, 4s, ... trần 5 phút: 20 lần thử ≈ 1 giờ. Broker sập ngắn hơn thế thì event vẫn tự đi
     * khi broker sống lại; lâu hơn thì chuyển FAILED và log ERROR để người vận hành xử lý.
     */
    static final int MAX_ATTEMPTS = 20;

    private final OutboxEventRepository outboxEventRepository;
    private final ConfirmedRabbitSender confirmedRabbitSender;

    /**
     * Đường nhanh: gửi ngay sau khi transaction nghiệp vụ commit.
     */
    @Async
    // AFTER_COMMIT nghia la transaction nghiep vu da dong, nen viec cap nhat trang thai
    // outbox phai chay trong transaction moi. Spring tu choi @Transactional mac dinh o day.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOutboxCreated(OutboxCreatedEvent springEvent) {
        // Rỗng nghĩa là scanner đang giữ khoá dòng này — để scanner gửi, không gửi trùng.
        outboxEventRepository.findByIdForPublish(springEvent.outboxEventId())
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

    /**
     * Mỗi dòng xử lý độc lập: dòng này lỗi chỉ lùi lịch dòng này, các dòng khác trong batch vẫn đi tiếp.
     */
    private void publish(OutboxEvent event) {
        if (OutboxEventStatus.PUBLISHED.equals(event.getStatus())) {
            return;
        }

        try {
            Message message = MessageBuilder
                    .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    // Consumer dùng id này làm khoá idempotency (ProcessedMessageService).
                    .setHeader(RabbitTopology.OUTBOX_EVENT_ID_HEADER, event.getId())
                    .build();

            // Chỉ trả về khi broker đã ack và route được; mọi trường hợp khác ném exception.
            confirmedRabbitSender.send(RabbitTopology.EXCHANGE, event.getRoutingKey(), message, event.getId());

            event.markPublished();
        } catch (Exception ex) {
            event.scheduleRetry(ex.getMessage(), MAX_ATTEMPTS);
            if (event.getStatus() == OutboxEventStatus.FAILED) {
                log.error("Outbox event [ID: {}, type: {}] FAILED sau {} lần gửi, cần xử lý tay: {}",
                        event.getId(), event.getEventType(), event.getAttempts(), ex.getMessage(), ex);
            } else {
                log.warn("Gửi outbox event [ID: {}] thất bại lần {}, thử lại lúc {}: {}",
                        event.getId(), event.getAttempts(), event.getNextAttemptAt(), ex.getMessage());
            }
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
