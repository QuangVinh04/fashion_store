package com.fashionstore.payment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.order.OrderDeliveredEvent;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fashionstore.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderDeliveredEventListenerTest {

    @Test
    void deliveredOrderSettlesCodPayment() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        ProcessedMessageService processedMessageService = mock(ProcessedMessageService.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        OrderDeliveredEventListener listener = new OrderDeliveredEventListener(
                paymentRepository, processedMessageService, objectMapper
        );
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return null;
        }).when(processedMessageService).processOnce(anyString(), anyString(), any(Runnable.class));

        Payment payment = Payment.builder()
                .orderId("order-1")
                .userId("user-1")
                .method(PaymentMethod.COD)
                .provider(PaymentProvider.COD)
                .status(PaymentStatus.COD_PENDING)
                .amount(new BigDecimal("450000"))
                .currency("VND")
                .build();
        LocalDateTime deliveredAt = LocalDateTime.of(2026, 9, 21, 10, 30);
        when(paymentRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(payment));
        EventEnvelope<OrderDeliveredEvent> envelope = EventEnvelope.v1(
                EventTypes.ORDER_DELIVERED,
                "order-1",
                "order-1",
                new OrderDeliveredEvent("order-1", deliveredAt)
        );

        listener.handle(envelope, "delivery-message-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getPaidAt()).isEqualTo(deliveredAt);
        verify(paymentRepository).save(payment);
    }
}
