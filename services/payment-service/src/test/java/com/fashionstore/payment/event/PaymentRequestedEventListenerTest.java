package com.fashionstore.payment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.payment.command.AuthorizePaymentCommand;
import com.fashionstore.contracts.payment.command.RefundPaymentCommand;
import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.dto.PaymentRefundResult;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.PaymentRefund;
import com.fashionstore.payment.entity.enumeration.PaymentRefundStatus;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fashionstore.payment.outbox.OutboxService;
import com.fashionstore.payment.repository.PaymentRefundRepository;
import com.fashionstore.payment.repository.PaymentRepository;
import com.fashionstore.payment.service.provider.PaymentHandler;
import com.fashionstore.payment.service.provider.PaymentHandlerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRequestedEventListenerTest {

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    PaymentRefundRepository paymentRefundRepository;
    @Mock
    ProcessedMessageService processedMessageService;
    @Mock
    OutboxService outboxService;
    @Mock
    PaymentHandlerRegistry paymentHandlerRegistry;
    @Mock
    PaymentHandler paymentHandler;

    PaymentRequestedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentRequestedEventListener(
                paymentRepository,
                paymentRefundRepository,
                processedMessageService,
                new ObjectMapper(),
                outboxService,
                paymentHandlerRegistry
        );
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return null;
        }).when(processedMessageService).processOnce(anyString(), anyString(), any(Runnable.class));
    }

    @Test
    void codRequestKeepsPaymentUnpaidWhileAllowingOrderFulfilment() {
        AuthorizePaymentCommand command = new AuthorizePaymentCommand(
                "order-1", "user-1", "COD", "COD", new BigDecimal("450000"), "VND", "127.0.0.1"
        );
        EventEnvelope<AuthorizePaymentCommand> envelope = EventEnvelope.v1(
                EventTypes.PAYMENT_REQUESTED, "order-1", "saga-1", command
        );
        when(paymentRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        listener.handle(envelope, "message-1");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.COD_PENDING);
        assertThat(captor.getValue().getPaidAt()).isNull();
        verify(outboxService).saveMessage(eq("order-1"), eq(EventTypes.PAYMENT_COMPLETED), any(EventEnvelope.class));
    }

    @Test
    void onlinePaymentRequestInitiatesGatewayImmediatelyAndPublishesUrl() {
        AuthorizePaymentCommand command = new AuthorizePaymentCommand(
                "order-1", "user-1", "ONLINE", "VNPAY", new BigDecimal("450000"), "VND", "203.0.113.9"
        );
        EventEnvelope<AuthorizePaymentCommand> envelope = EventEnvelope.v1(
                EventTypes.PAYMENT_REQUESTED, "order-1", "saga-1", command
        );
        when(paymentRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", "payment-1");
            }
            return saved;
        });
        when(paymentHandlerRegistry.get(PaymentProvider.VNPAY)).thenReturn(paymentHandler);
        when(paymentHandler.initiate(any(Payment.class), eq("203.0.113.9"))).thenReturn(
                PaymentInitiationResult.builder()
                        .paymentUrl("https://sandbox.vnpayment.vn/pay?...")
                        .merchantReference("merchant-1")
                        .providerAmount(new BigDecimal("450000"))
                        .providerCurrency("VND")
                        .build());

        listener.handle(envelope, "message-1");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(captor.getValue().getMerchantReference()).isNotBlank();
        verify(outboxService).saveMessage(eq("order-1"), eq(EventTypes.PAYMENT_INITIATED), any(EventEnvelope.class));
    }

    @Test
    void completedProviderRefundIsAuditedAndMarksPaymentRefunded() {
        Payment payment = onlineCompletedPayment();
        RefundPaymentCommand command = new RefundPaymentCommand(
                "order-1", "payment-1", new BigDecimal("450000"), "Customer return"
        );
        EventEnvelope<RefundPaymentCommand> envelope = EventEnvelope.v1(
                EventTypes.PAYMENT_REFUND_REQUESTED, "order-1", "order-1", command
        );
        when(paymentRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(payment));
        when(paymentRefundRepository.findByIdempotencyKey("message-refund-1")).thenReturn(Optional.empty());
        when(paymentRefundRepository.sumCompletedAmountByPaymentId("payment-1")).thenReturn(BigDecimal.ZERO);
        when(paymentRefundRepository.save(any(PaymentRefund.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentHandlerRegistry.get(PaymentProvider.VNPAY)).thenReturn(paymentHandler);
        when(paymentHandler.refund(any(Payment.class), any(PaymentRefund.class)))
                .thenReturn(PaymentRefundResult.completed("vnpay-refund-1"));

        listener.handle(envelope, "message-refund-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        ArgumentCaptor<PaymentRefund> captor = ArgumentCaptor.forClass(PaymentRefund.class);
        verify(paymentRefundRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentRefundStatus.COMPLETED);
        assertThat(captor.getValue().getProviderRefundId()).isEqualTo("vnpay-refund-1");
        verify(outboxService).saveMessage(eq("order-1"), eq(EventTypes.PAYMENT_REFUNDED), any(EventEnvelope.class));
    }

    private Payment onlineCompletedPayment() {
        Payment payment = Payment.builder()
                .orderId("order-1")
                .userId("user-1")
                .method(PaymentMethod.ONLINE)
                .provider(PaymentProvider.VNPAY)
                .status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("450000"))
                .currency("VND")
                .transactionId("capture-1")
                .build();
        ReflectionTestUtils.setField(payment, "id", "payment-1");
        return payment;
    }
}
