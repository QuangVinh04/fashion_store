package com.fashionstore.payment.payment.service;

import com.fashionstore.payment.wallet.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.messaging.processed.ProcessedMessageService;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.contracts.common.EventEnvelope;
import com.fashionstore.contracts.common.EventTypes;
import com.fashionstore.contracts.payment.command.AuthorizePaymentCommand;
import com.fashionstore.contracts.payment.command.CancelPaymentCommand;
import com.fashionstore.payment.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.payment.entity.Payment;
import com.fashionstore.payment.payment.entity.PaymentStatus;
import com.fashionstore.payment.shared.outbox.OutboxEventRecorder;
import com.fashionstore.payment.payment.repository.PaymentRepository;
import com.fashionstore.payment.payment.client.PaymentHandler;
import com.fashionstore.payment.payment.client.PaymentHandlerRegistry;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceTest {

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    ProcessedMessageService processedMessageService;
    @Mock
    OutboxEventRecorder outboxEventRecorder;
    @Mock
    PaymentHandlerRegistry paymentHandlerRegistry;
    @Mock
    PaymentHandler paymentHandler;

    @Mock
    WalletService walletService;

    PaymentService service;
    Payment storedPayment;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(paymentRepository.findByOrderId("order-1"))
                .thenAnswer(call -> Optional.ofNullable(storedPayment));
        org.mockito.Mockito.lenient().when(paymentRepository.findByIdForUpdate(anyString()))
                .thenAnswer(call -> Optional.ofNullable(storedPayment));
        org.mockito.Mockito.lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(call -> {
            storedPayment = call.getArgument(0);
            return storedPayment;
        });
        var jdbcTemplate = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        org.mockito.Mockito.lenient().when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(5000000000000000L);
        PaymentTransactionService database = new PaymentTransactionService(paymentRepository, jdbcTemplate,
                processedMessageService, outboxEventRecorder, org.mockito.Mockito.mock(PaymentStateService.class), org.mockito.Mockito.mock(WalletService.class));
        service = new com.fashionstore.payment.payment.service.impl.PaymentServiceImpl(paymentRepository, paymentHandlerRegistry,
                org.mockito.Mockito.mock(com.fashionstore.payment.payment.mapper.PaymentResponseMapper.class),
                org.mockito.Mockito.mock(com.fashionstore.common.security.CurrentUserProvider.class),
                database, org.mockito.Mockito.mock(CallbackPaymentService.class), walletService);
        // proxy = new org.springframework.aop.framework.ProxyFactory(listener);
        // proxy
        // proxy(new org.springframework.transaction.interceptor.TransactionInterceptor(new InMemoryTransactionManager(),
                // new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        
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

        service.authorize((AuthorizePaymentCommand) envelope.payload(), "message-1", envelope.correlationId());;

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.COD_PENDING);
        assertThat(captor.getValue().getPaidAt()).isNull();
        verify(outboxEventRecorder).saveMessage(eq("order-1"), eq(EventTypes.PAYMENT_COMPLETED), any(EventEnvelope.class));
    }

    @Test
    void cancellingAnAlreadyCapturedPaymentRejectsWithItsPaymentId() {
        storedPayment = onlineCompletedPayment();
        when(paymentRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(storedPayment));

        service.cancel(new CancelPaymentCommand("order-1", "payment-1", "Quá hạn"), "message-cancel", "saga-1");

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxEventRecorder).saveMessage(
                eq("order-1"), eq(EventTypes.PAYMENT_CANCELLATION_REJECTED), captor.capture());
        var event = (com.fashionstore.contracts.payment.event.PaymentCancellationRejectedEvent) captor.getValue().payload();
        assertThat(event.paymentId()).isEqualTo("payment-1");
        assertThat(event.failureCode()).isEqualTo("PAYMENT_ALREADY_CAPTURED");
    }

    @Test
    void onlinePaymentRequestOnlyRecordsPaymentWithoutCallingGateway() {
        EventEnvelope<AuthorizePaymentCommand> envelope = onlineRequest();
        when(paymentRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.empty());
        stubPaymentSave();

        service.authorize(envelope.payload(), "message-1", envelope.correlationId());

        assertThat(storedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(storedPayment.getMerchantReference()).isNotBlank();
        assertThat(storedPayment.getProviderAmount()).isEqualByComparingTo("450000");
        assertThat(storedPayment.getProviderCurrency()).isEqualTo("VND");
        assertThat(storedPayment.getProviderTransactionDate()).isNotBlank();
        verifyNoInteractions(paymentHandlerRegistry);
        verify(outboxEventRecorder, never()).saveMessage(anyString(), anyString(), any(EventEnvelope.class));
    }

    @Test
    void walletPaymentRequestPreparesWalletSessionWithoutGateway() {
        EventEnvelope<AuthorizePaymentCommand> envelope = EventEnvelope.v1(EventTypes.PAYMENT_REQUESTED, "order-1", "saga-1",
                new AuthorizePaymentCommand("order-1", "user-1", "WALLET", "INTERNAL", new BigDecimal("450000"), "VND",
                        "203.0.113.9", java.time.Instant.now().plusSeconds(900)));
        when(paymentRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.empty());
        stubPaymentSave();

        service.authorize(envelope.payload(), "message-1", envelope.correlationId());

        verify(walletService).prepare(storedPayment);
        verifyNoInteractions(paymentHandlerRegistry);
    }

    @Test
    void cancellingPendingOnlinePaymentAlwaysReplies() {
        Payment payment = onlineCompletedPayment();
        payment.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(payment));

        service.cancel(new CancelPaymentCommand("order-1", "payment-1", "Quá hạn"), "cancel-1", "saga-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(outboxEventRecorder).saveMessage(eq("order-1"), eq(EventTypes.PAYMENT_CANCELLED), any(EventEnvelope.class));
    }

    @Test
    void cancellingLegacyUnknownInitiationStillReplies() {
        Payment payment = onlineCompletedPayment();
        payment.setStatus(PaymentStatus.INITIATION_UNKNOWN);
        when(paymentRepository.findByOrderIdForUpdate("order-1")).thenReturn(Optional.of(payment));

        service.cancel(new CancelPaymentCommand("order-1", "payment-1", "cancel"), "cancel-1", "saga-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(outboxEventRecorder).saveMessage(eq("order-1"), eq(EventTypes.PAYMENT_CANCELLED), any(EventEnvelope.class));
    }

    private EventEnvelope<AuthorizePaymentCommand> onlineRequest() {
        return EventEnvelope.v1(EventTypes.PAYMENT_REQUESTED, "order-1", "saga-1", new AuthorizePaymentCommand(
                "order-1", "user-1", "ONLINE", "VNPAY", new BigDecimal("450000"), "VND", "203.0.113.9"));
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
