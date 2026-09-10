package com.fashionstore.payment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.payment.command.CancelPaymentCommand;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.PaymentStatus;
import com.fashionstore.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCancellationEventListenerTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ProcessedMessageService processedMessageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PaymentRequestedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentRequestedEventListener(
                paymentRepository,
                processedMessageService,
                new ObjectMapper(),
                eventPublisher
        );
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return null;
        }).when(processedMessageService).processOnce(anyString(), anyString(), any(Runnable.class));
    }

    @Test
    void cancelsPendingPayment() {
        Payment payment = payment(PaymentStatus.PENDING);
        when(paymentRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(payment));

        listener.handle(cancellationEnvelope(), "message-1");

        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
        assertEquals(EventTypes.PAYMENT_CANCELLED, publishedEvent().eventType());
    }

    @Test
    void rejectsCancellationWhenPaymentAlreadyCompleted() {
        Payment payment = payment(PaymentStatus.COMPLETED);
        when(paymentRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(payment));

        listener.handle(cancellationEnvelope(), "message-1");

        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        assertEquals(EventTypes.PAYMENT_CANCELLATION_REJECTED, publishedEvent().eventType());
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = Payment.builder()
                .orderId("order-1")
                .userId("user-1")
                .status(status)
                .build();
        payment.setId("payment-1");
        return payment;
    }

    private EventEnvelope<CancelPaymentCommand> cancellationEnvelope() {
        return EventEnvelope.v1(
                EventTypes.PAYMENT_CANCELLATION_REQUESTED,
                "order-1",
                "correlation-1",
                new CancelPaymentCommand("order-1", null, "Payment timed out")
        );
    }

    private EventEnvelope<?> publishedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return (EventEnvelope<?>) captor.getValue();
    }
}
