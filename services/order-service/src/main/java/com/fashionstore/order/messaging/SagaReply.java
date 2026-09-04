package com.fashionstore.order.messaging;

import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.order.model.OrderSaga;
import com.fashionstore.order.model.enumeration.OrderSagaStep;
import lombok.Builder;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Khai báo một reply handler dưới dạng dữ liệu thuần: nhận message nào, thuộc bước nào, và đổi saga ra sao.
 * Không có hạ tầng ở đây — dedupe, lock, guard, outbox nằm trong {@link SagaReplyProcessor}.
 *
 * @param orderIdOf  rút orderId khỏi payload để đối chiếu với saga (chống message lạc)
 * @param onStep     nhánh chính, chỉ chạy khi saga đang đứng đúng {@code expectedStep}
 * @param onTerminal nhánh dọn dẹp khi saga đã đóng (§08); bỏ trống nghĩa là không làm gì
 */
@Builder
public record SagaReply<T>(
        String messageId,
        String consumer,
        EventEnvelope<?> envelope,
        Class<T> payloadType,
        Function<T, String> orderIdOf,
        OrderSagaStep expectedStep,
        BiFunction<OrderSaga, T, List<SagaCommand>> onStep,
        BiFunction<OrderSaga, T, List<SagaCommand>> onTerminal
) {

    public SagaReply {
        if (onTerminal == null) {
            onTerminal = (saga, payload) -> List.of();
        }
    }
}
