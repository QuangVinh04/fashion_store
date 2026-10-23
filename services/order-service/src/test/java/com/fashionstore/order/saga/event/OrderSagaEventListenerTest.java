package com.fashionstore.order.saga.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.order.shared.config.messaging.RabbitMQNames;
import com.fashionstore.order.saga.service.OrderSagaService;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.util.Map;

class OrderSagaEventListenerTest {

    @Test
    void passesPaymentEnvelopeAndMessageIdentityToBusinessService() {
        OrderSagaService service = mock(OrderSagaService.class);
        OrderSagaEventListener listener = new OrderSagaEventListener(service);
        EventEnvelope<?> envelope =
                EventEnvelope.v1(EventTypes.PAYMENT_COMPLETED, "order-1", "saga-1", "payload");

        listener.paymentCompleted(envelope, "message-1");

        verify(service).paymentCompleted(envelope, "message-1");
        verifyNoMoreInteractions(service);
    }

    @Test
    void passesInventorySuccessAndRejectionWithoutChangingTheirIdentity() {
        OrderSagaService service = mock(OrderSagaService.class);
        OrderSagaEventListener listener = new OrderSagaEventListener(service);
        EventEnvelope<?> envelope =
                EventEnvelope.v1(EventTypes.INVENTORY_RESERVED, "order-1", "saga-1", "payload");

        listener.inventoryReserved(envelope, "reserved-message");
        listener.inventoryConfirmationRejected(envelope, "rejected-message");

        verify(service).inventoryReserved(envelope, "reserved-message");
        verify(service).inventoryConfirmationRejected(envelope, "rejected-message");
        verifyNoMoreInteractions(service);
    }

    @Test
    void preservesAllTenExistingRabbitSubscriptions() throws Exception {
        Map<String, String> queues =
                Map.ofEntries(
                        Map.entry(
                                "inventoryConfirmationRejected",
                                RabbitMQNames.ORDER_INVENTORY_CONFIRMATION_REJECTED_QUEUE),
                        Map.entry(
                                "inventoryReserved", RabbitMQNames.ORDER_INVENTORY_RESERVED_QUEUE),
                        Map.entry("paymentInitiated", RabbitMQNames.ORDER_PAYMENT_INITIATED_QUEUE),
                        Map.entry(
                                "inventoryRejected", RabbitMQNames.ORDER_INVENTORY_REJECTED_QUEUE),
                        Map.entry("paymentCompleted", RabbitMQNames.ORDER_PAYMENT_COMPLETED_QUEUE),
                        Map.entry("paymentFailed", RabbitMQNames.ORDER_PAYMENT_FAILED_QUEUE),
                        Map.entry("paymentCancelled", RabbitMQNames.ORDER_PAYMENT_CANCELLED_QUEUE),
                        Map.entry(
                                "paymentCancellationRejected",
                                RabbitMQNames.ORDER_PAYMENT_CANCELLATION_REJECTED_QUEUE),
                        Map.entry(
                                "inventoryConfirmed",
                                RabbitMQNames.ORDER_INVENTORY_CONFIRMED_QUEUE),
                        Map.entry(
                                "inventoryReleased", RabbitMQNames.ORDER_INVENTORY_RELEASED_QUEUE));

        for (var entry : queues.entrySet()) {
            var method =
                    OrderSagaEventListener.class.getDeclaredMethod(
                            entry.getKey(), EventEnvelope.class, String.class);
            assertThat(method.getAnnotation(RabbitListener.class).queues())
                    .containsExactly(entry.getValue());
        }
    }
}
