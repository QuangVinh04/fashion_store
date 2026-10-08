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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
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
    Payment storedPayment;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactions = new TransactionTemplate(new InMemoryTransactionManager());
        org.mockito.Mockito.lenient().when(paymentRepository.findByOrderId("order-1"))
                .thenAnswer(call -> Optional.ofNullable(storedPayment));
        org.mockito.Mockito.lenient().when(paymentRepository.findByIdForUpdate(anyString()))
                .thenAnswer(call -> Optional.ofNullable(storedPayment));
        org.mockito.Mockito.lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(call -> {
            storedPayment = call.getArgument(0);
            return storedPayment;
        });
        com.fashionstore.payment.service.PaymentService paymentService =
                new com.fashionstore.payment.service.impl.PaymentServiceImpl(paymentRepository, paymentHandlerRegistry,
                        org.mockito.Mockito.mock(com.fashionstore.payment.mapper.PaymentResponseMapper.class),
                        org.mockito.Mockito.mock(com.fashionstore.common.security.CurrentUserProvider.class),
                        transactions, outboxService,
                        org.mockito.Mockito.mock(com.fashionstore.payment.service.CallbackPaymentService.class));
        listener = new PaymentRequestedEventListener(
                paymentRepository,
                paymentRefundRepository,
                processedMessageService,
                new ObjectMapper(),
                outboxService,
                paymentHandlerRegistry,
                transactions,
                paymentService
        );
        org.springframework.aop.framework.ProxyFactory proxy = new org.springframework.aop.framework.ProxyFactory(listener);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(new InMemoryTransactionManager(),
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        listener = (PaymentRequestedEventListener) proxy.getProxy();
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
            storedPayment = saved;
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", "payment-1");
            }
            return saved;
        });
        when(paymentHandlerRegistry.get(PaymentProvider.VNPAY)).thenReturn(paymentHandler);
        reloadReturnsSavedPayment();
        when(paymentHandler.initiate(any(Payment.class), eq("203.0.113.9"))).thenAnswer(invocation -> {
            // Đang chờ cổng thanh toán trả lời thì không được giữ transaction / row lock nào.
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return PaymentInitiationResult.builder()
                    .paymentUrl("https://sandbox.vnpayment.vn/pay?...")
                    .merchantReference("merchant-1")
                    .providerAmount(new BigDecimal("450000"))
                    .providerCurrency("VND")
                    .build();
        });

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
        stubRefundPersistence(payment);
        when(paymentHandlerRegistry.get(PaymentProvider.VNPAY)).thenReturn(paymentHandler);
        when(paymentHandler.refund(any(Payment.class), any(PaymentRefund.class))).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return PaymentRefundResult.completed("vnpay-refund-1");
        });

        listener.handle(envelope, "message-refund-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        ArgumentCaptor<PaymentRefund> captor = ArgumentCaptor.forClass(PaymentRefund.class);
        verify(paymentRefundRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentRefundStatus.COMPLETED);
        assertThat(captor.getValue().getProviderRefundId()).isEqualTo("vnpay-refund-1");
        verify(outboxService).saveMessage(eq("order-1"), eq(EventTypes.PAYMENT_REFUNDED), any(EventEnvelope.class));
    }

    @Test
    void gatewayTimeoutDoesNotPublishPaymentFailed() {
        EventEnvelope<AuthorizePaymentCommand> envelope = onlineRequest();
        when(paymentRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.empty());
        stubPaymentSave();
        reloadReturnsSavedPayment();
        when(paymentHandlerRegistry.get(PaymentProvider.VNPAY)).thenReturn(paymentHandler);
        when(paymentHandler.initiate(any(Payment.class), anyString())).thenThrow(new IllegalStateException("gateway timeout"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> listener.handle(envelope, "message-1"))
                .isInstanceOf(IllegalStateException.class).hasMessage("gateway timeout");

        assertThat(lastSavedPayment().getStatus()).isEqualTo(PaymentStatus.INITIATION_UNKNOWN);
        verify(outboxService, never()).saveMessage(eq("order-1"), eq(EventTypes.PAYMENT_FAILED), any(EventEnvelope.class));
        verify(outboxService, never()).saveMessage(eq("order-1"), eq(EventTypes.PAYMENT_INITIATED), any(EventEnvelope.class));
    }

    @Test
    void paymentCancelledWhileGatewayWasBeingCalledIsNotOverwritten() {
        EventEnvelope<AuthorizePaymentCommand> envelope = onlineRequest();
        when(paymentRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.empty());
        stubPaymentSave();
        when(paymentHandlerRegistry.get(PaymentProvider.VNPAY)).thenReturn(paymentHandler);
        when(paymentHandler.initiate(any(Payment.class), anyString())).thenAnswer(call -> {
            // Giả lập trạng thái đã đổi trước bước ghi kết quả provider.
            storedPayment.setStatus(PaymentStatus.CANCELLED);
            return PaymentInitiationResult.builder().paymentUrl("https://pay").build();
        });

        listener.handle(envelope, "message-1");

        assertThat(storedPayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(outboxService, never()).saveMessage(anyString(), eq(EventTypes.PAYMENT_INITIATED), any(EventEnvelope.class));
    }

    @Test
    void providerRefundFailureMarksRefundFailedAndRepliesRejected() {
        Payment payment = onlineCompletedPayment();
        EventEnvelope<RefundPaymentCommand> envelope = EventEnvelope.v1(
                EventTypes.PAYMENT_REFUND_REQUESTED, "order-1", "order-1",
                new RefundPaymentCommand("order-1", "payment-1", new BigDecimal("450000"), "Customer return"));
        when(paymentRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(payment));
        when(paymentRefundRepository.findByIdempotencyKey("message-refund-2")).thenReturn(Optional.empty());
        when(paymentRefundRepository.sumCompletedAmountByPaymentId("payment-1")).thenReturn(BigDecimal.ZERO);
        PaymentRefund[] saved = stubRefundPersistence(payment);
        when(paymentHandlerRegistry.get(PaymentProvider.VNPAY)).thenReturn(paymentHandler);
        when(paymentHandler.refund(any(Payment.class), any(PaymentRefund.class))).thenThrow(new IllegalStateException("VNPay 94"));

        listener.handle(envelope, "message-refund-2");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
        assertThat(saved[0].getStatus()).isEqualTo(PaymentRefundStatus.FAILED);
        verify(outboxService).saveMessage(eq("order-1"), eq(EventTypes.PAYMENT_REFUND_REJECTED), any(EventEnvelope.class));
    }

    private EventEnvelope<AuthorizePaymentCommand> onlineRequest() {
        return EventEnvelope.v1(EventTypes.PAYMENT_REQUESTED, "order-1", "saga-1", new AuthorizePaymentCommand(
                "order-1", "user-1", "ONLINE", "VNPAY", new BigDecimal("450000"), "VND", "203.0.113.9"));
    }

    @Test
    void redeliveryRecoversInitiationEvenWhenPreparationMessageWasProcessed() {
        storedPayment = onlineCompletedPayment();
        storedPayment.setStatus(PaymentStatus.PENDING);
        when(paymentHandlerRegistry.get(PaymentProvider.VNPAY)).thenReturn(paymentHandler);
        when(paymentHandler.initiate(any(Payment.class), anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return PaymentInitiationResult.builder().paymentUrl("https://pay")
                    .providerAmount(new BigDecimal("450000")).providerCurrency("VND").build();
        });
        // Marker đã commit trước khi process chết: bước chuẩn bị DB không chạy lại.
        org.mockito.Mockito.reset(processedMessageService);
        doAnswer(call -> null).when(processedMessageService)
                .processOnce(eq("message-1"), eq("payment-requested-v2"), any(Runnable.class));

        listener.handle(onlineRequest(), "message-1");

        assertThat(storedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(storedPayment.getPaymentUrl()).isEqualTo("https://pay");
        verify(paymentHandler).initiate(any(Payment.class), eq("203.0.113.9"));
        verify(outboxService).saveMessage(eq("order-1"), eq(EventTypes.PAYMENT_INITIATED), any(EventEnvelope.class));
    }

    @Test
    void cancellationCannotFinalizeWhileInitiationResultIsUnknown() {
        Payment payment = onlineCompletedPayment();
        payment.setStatus(PaymentStatus.INITIATION_UNKNOWN);
        when(paymentRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(payment));
        var command = new com.fashionstore.contracts.payment.command.CancelPaymentCommand("order-1", "payment-1", "cancel");
        var envelope = EventEnvelope.v1(EventTypes.PAYMENT_CANCELLATION_REQUESTED, "order-1", "saga-1", command);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> listener.handle(envelope, "cancel-1"))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.INITIATION_UNKNOWN);
        verify(outboxService, never()).saveMessage(anyString(), eq(EventTypes.PAYMENT_CANCELLED), any());
    }

    private void stubPaymentSave() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            storedPayment = saved;
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", "payment-1");
            }
            return saved;
        });
    }

    /** Pha 3 đọc lại payment theo id (có khoá); trong unit test chính là object đã lưu ở pha 1. */
    private void reloadReturnsSavedPayment() {
        when(paymentRepository.findByIdForUpdate("payment-1")).thenAnswer(invocation -> Optional.of(lastSavedPayment()));
    }

    private Payment lastSavedPayment() {
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private PaymentRefund[] stubRefundPersistence(Payment payment) {
        PaymentRefund[] saved = new PaymentRefund[1];
        when(paymentRefundRepository.save(any(PaymentRefund.class))).thenAnswer(invocation -> {
            PaymentRefund refund = invocation.getArgument(0);
            if (refund.getId() == null) {
                ReflectionTestUtils.setField(refund, "id", "refund-1");
            }
            saved[0] = refund;
            return refund;
        });
        when(paymentRefundRepository.findById("refund-1")).thenAnswer(invocation -> Optional.ofNullable(saved[0]));
        when(paymentRepository.findByIdForUpdate("payment-1")).thenReturn(Optional.of(payment));
        return saved;
    }

    /**
     * Transaction manager giả, không có database: chỉ bật/tắt cờ "đang trong transaction" của Spring để
     * test kiểm tra được đoạn code nào chạy trong transaction, đoạn nào chạy ngoài.
     */
    static class InMemoryTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
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
