package com.fashionstore.order.saga.event;

import com.fashionstore.order.shared.outbox.OutboxEventRecorder;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.order.saga.dto.SagaCommand;
import com.fashionstore.order.saga.entity.OrderSaga;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * Đường ra duy nhất của saga. Mọi message đi khỏi order-service đều qua đây, trong cùng transaction
 * nghiệp vụ, nên không có chỗ nào gọi thẳng RabbitTemplate.
 *
 * <p>Đây cũng là nơi ép luật correlationId = sagaId: handler không tự dựng envelope nên không thể
 * quên.
 */
@Component
@RequiredArgsConstructor
public class SagaOutbox {

    private final OutboxEventRecorder outboxEventRecorder;

    public void emit(OrderSaga saga, SagaCommand command) {
        emit(saga.getOrderId(), saga.getId(), command);
    }

    /**
     * Cho các command đứng ngoài saga đặt hàng (ví dụ hoàn tiền sau khi đơn đã RETURNED từ lâu) —
     * không có sagaId để làm correlationId, nên dùng thẳng orderId.
     */
    public void emit(String orderId, String correlationId, SagaCommand command) {
        outboxEventRecorder.saveMessage(
                orderId,
                command.eventType(),
                EventEnvelope.v1(command.eventType(), orderId, correlationId, command.payload()));
    }
}
