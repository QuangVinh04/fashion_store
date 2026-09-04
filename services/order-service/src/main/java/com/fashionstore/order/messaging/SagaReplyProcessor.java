package com.fashionstore.order.messaging;

import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.order.model.OrderSaga;
import com.fashionstore.order.repository.OrderSagaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Khung xử lý chung cho mọi reply handler của saga.
 *
 * <p>Trình tự cố định, không handler nào được tự viết lại:
 * <ol>
 *   <li>bỏ message trùng ở tầng processed_message;</li>
 *   <li>đọc saga theo correlationId với pessimistic lock;</li>
 *   <li>ba guard — saga tồn tại, orderId khớp, đúng bước — sai thì <b>return, không throw</b>;</li>
 *   <li>chạy transition rồi ghi command kế tiếp vào outbox, trong cùng transaction.</li>
 * </ol>
 *
 * <p>Guard trả về im lặng vì sai bước là chuyện bình thường của hệ bất đồng bộ: reply của vòng saga cũ,
 * reply lặp lại, reply về sau timeout. Throw ở đó chỉ khiến Rabbit requeue một message không bao giờ
 * xử lý được. Ngược lại, lỗi hạ tầng (DB, Redis, deserialize) vẫn ném ra ngoài để container retry rồi
 * đẩy sang DLQ — đó là loại lỗi có thể tự khỏi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaReplyProcessor {

    private final ProcessedMessageService processedMessageService;
    private final OrderSagaRepository sagaRepository;
    private final SagaOutbox sagaOutbox;
    private final ObjectMapper objectMapper;

    public <T> void process(SagaReply<T> reply) {
        processedMessageService.processOnce(reply.messageId(), reply.consumer(), () -> {
            T payload = objectMapper.convertValue(reply.envelope().payload(), reply.payloadType());
            String sagaId = reply.envelope().correlationId();

            OrderSaga saga = sagaRepository.findByIdForUpdate(sagaId).orElse(null);
            if (saga == null) {
                log.warn("[{}] saga {} không tồn tại — bỏ qua message {}",
                        reply.consumer(), sagaId, reply.messageId());
                return;
            }

            String payloadOrderId = reply.orderIdOf().apply(payload);
            if (!saga.getOrderId().equals(payloadOrderId)) {
                log.error("[{}] message lạc: saga {} thuộc order {} nhưng payload nói order {}",
                        reply.consumer(), sagaId, saga.getOrderId(), payloadOrderId);
                return;
            }

            List<SagaCommand> next = saga.isTerminal()
                    ? onTerminal(reply, saga, payload)
                    : onStep(reply, saga, payload);

            sagaRepository.save(saga);
            next.forEach(command -> sagaOutbox.emit(saga, command));
        });
    }

    private <T> List<SagaCommand> onStep(SagaReply<T> reply, OrderSaga saga, T payload) {
        if (saga.getCurrentStep() != reply.expectedStep()) {
            log.info("[{}] saga {} đang ở bước {}, reply thuộc bước {} — bỏ qua",
                    reply.consumer(), saga.getId(), saga.getCurrentStep(), reply.expectedStep());
            return List.of();
        }
        return reply.onStep().apply(saga, payload);
    }

    private <T> List<SagaCommand> onTerminal(SagaReply<T> reply, OrderSaga saga, T payload) {
        log.info("[{}] saga {} đã ở trạng thái kết thúc {} — chạy nhánh dọn dẹp",
                reply.consumer(), saga.getId(), saga.getStatus());
        return reply.onTerminal().apply(saga, payload);
    }
}
