package com.fashionstore.order.messaging;

import com.fashionstore.order.model.Order;
import com.fashionstore.order.model.OrderSaga;
import com.fashionstore.order.model.enumeration.OrderSagaStatus;
import com.fashionstore.order.model.enumeration.OrderSagaStep;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderSagaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Yêu cầu hủy đến từ khách hàng, không phải từ một reply. Nó vẫn phải đi qua state machine chứ không được
 * set thẳng đơn về CANCELLED: kho có thể đang giữ và tiền có thể đang treo.
 *
 * <p>Quy tắc theo bước hiện tại của saga:
 * <ul>
 *   <li>{@code RESERVE_INVENTORY} — chưa giữ được gì, đóng saga ngay. Reply giữ kho đến sau sẽ rơi vào
 *       nhánh terminal của handler và tự nhả kho.</li>
 *   <li>{@code AUTHORIZE_PAYMENT} — người dùng có thể đang thao tác dở trên cổng thanh toán, nên hỏi
 *       payment-service trước: hủy được thì nhả kho, đã thu tiền rồi thì saga tự đi tiếp (forward recovery).</li>
 *   <li>{@code CONFIRM_INVENTORY} — tiền đã thu, hủy lúc này là nghiệp vụ hoàn tiền, không phải bù trừ saga.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaCancellationService {

    public enum Outcome {
        /** Saga đóng ngay, đơn đã CANCELLED. */
        CANCELLED,
        /** Đã khởi động bù trừ; đơn về CANCELLED khi participant trả lời xong. */
        COMPENSATING,
        /** Bước hiện tại không cho phép khách tự hủy. */
        NOT_ALLOWED
    }

    private final OrderSagaRepository sagaRepository;
    private final OrderRepository orderRepository;
    private final SagaOutbox sagaOutbox;

    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome cancel(OrderSaga saga, Order order, String reason) {
        if (saga.isTerminal()) {
            return Outcome.NOT_ALLOWED;
        }
        if (saga.getStatus() == OrderSagaStatus.COMPENSATING) {
            // Đã có ai đó (timeout, participant, hoặc chính khách hàng) khởi động bù trừ rồi.
            return Outcome.COMPENSATING;
        }

        return switch (saga.getCurrentStep()) {
            case RESERVE_INVENTORY -> {
                saga.compensated("CANCELLED_BY_CUSTOMER", reason);
                sagaRepository.save(saga);

                order.cancel(reason);
                orderRepository.save(order);

                sagaOutbox.emit(saga, SagaCommands.orderCancelled(order, reason));
                log.info("Saga {} bị khách hủy trước khi giữ kho", saga.getId());
                yield Outcome.CANCELLED;
            }
            case AUTHORIZE_PAYMENT -> {
                saga.startCompensation(OrderSagaStep.CANCEL_PAYMENT, "CANCELLED_BY_CUSTOMER", reason);
                sagaRepository.save(saga);

                sagaOutbox.emit(saga, SagaCommands.cancelPayment(saga));
                log.info("Saga {} bắt đầu bù trừ theo yêu cầu của khách", saga.getId());
                yield Outcome.COMPENSATING;
            }
            default -> Outcome.NOT_ALLOWED;
        };
    }
}
