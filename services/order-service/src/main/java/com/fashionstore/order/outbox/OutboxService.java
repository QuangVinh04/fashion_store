package com.fashionstore.order.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Ghi message vào outbox trong chính transaction nghiệp vụ đang chạy — bắt buộc phải có transaction,
     * nếu không thì mất luôn bảo đảm "state và message cùng commit hoặc cùng biến mất".
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveMessage(String aggregateId, String eventType, Object payload) {
        try {
            OutboxEvent event = OutboxEvent.pending(
                    aggregateId,
                    eventType,
                    objectMapper.writeValueAsString(payload)
            );

            outboxRepository.save(event);

            // Gửi ngay sau commit; scanner định kỳ chỉ là lưới an toàn.
            eventPublisher.publishEvent(new OutboxCreatedEvent(event.getId()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Cannot serialize outbox message: " + eventType,
                    exception
            );
        }
    }
}
