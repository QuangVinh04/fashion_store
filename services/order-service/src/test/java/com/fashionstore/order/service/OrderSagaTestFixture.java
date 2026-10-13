package com.fashionstore.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.common.payment.*;
import com.fashionstore.contracts.common.*;
import com.fashionstore.contracts.payment.event.*;
import com.fashionstore.order.entity.*;
import com.fashionstore.order.entity.enumeration.*;
import com.fashionstore.order.event.SagaOutbox;
import com.fashionstore.order.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;

import java.util.*;

abstract class OrderSagaTestFixture {
    OrderSagaRepository sagas = mock(OrderSagaRepository.class);
    OrderRepository orders = mock(OrderRepository.class);
    SagaOutbox outbox = mock(SagaOutbox.class);
    OrderSagaService listener;
    OrderSaga saga;
    Order order;

    @BeforeEach
    void setup() {
        var messages = mock(ProcessedMessageService.class);
        doAnswer(
                        call -> {
                            call.getArgument(2, Runnable.class).run();
                            return null;
                        })
                .when(messages)
                .processOnce(anyString(), anyString(), any(Runnable.class));
        listener =
                new OrderSagaService(
                        messages,
                        sagas,
                        orders,
                        outbox,
                        new ObjectMapper().findAndRegisterModules(),
                        mock(CartRepository.class),
                        mock(com.fashionstore.order.service.PromotionService.class),
                        mock(OrderStatusHistoryRepository.class),
                        mock(com.fashionstore.order.service.OrderNotificationService.class));
        saga = OrderSaga.start("order-1");
        saga.inventoryReserved("reservation-1");
        order =
                Order.builder()
                        .paymentMethod(PaymentMethod.WALLET)
                        .paymentProvider(PaymentProvider.INTERNAL)
                        .status(OrderStatus.PENDING)
                        .paymentId("payment-1")
                        .paymentUrl("wallet-url")
                        .build();
        order.setId("order-1");
        when(sagas.findByIdForUpdate(saga.getId())).thenReturn(Optional.of(saga));
        when(orders.findById("order-1")).thenReturn(Optional.of(order));
        when(orders.findByIdForUpdate("order-1")).thenReturn(Optional.of(order));
    }
}
