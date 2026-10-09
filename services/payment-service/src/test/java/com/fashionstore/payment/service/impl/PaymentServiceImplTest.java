package com.fashionstore.payment.service.impl;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fashionstore.payment.mapper.PaymentResponseMapper;
import com.fashionstore.payment.repository.PaymentRepository;
import com.fashionstore.payment.service.PaymentService;
import com.fashionstore.payment.service.provider.PaymentHandler;
import com.fashionstore.payment.service.provider.PaymentHandlerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PaymentServiceImplTest {
    PaymentRepository repository = mock(PaymentRepository.class);
    PaymentHandlerRegistry registry = mock(PaymentHandlerRegistry.class);
    PaymentHandler handler = mock(PaymentHandler.class);
    CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    PaymentService service;
    PaymentServiceImpl target;
    Payment payment;

    @BeforeEach
    void setUp() {
        org.springframework.transaction.support.TransactionTemplate transactions =
                new org.springframework.transaction.support.TransactionTemplate(new TestTransactionManager());
        com.fashionstore.payment.outbox.OutboxService outbox = mock(com.fashionstore.payment.outbox.OutboxService.class);
        PaymentResponseMapper mapper = mock(PaymentResponseMapper.class);
        com.fashionstore.payment.service.CallbackPaymentService callbacks = new CallbackPaymentServiceImpl(registry, new com.fashionstore.payment.service.PaymentDbService(repository, null, null, outbox, new com.fashionstore.payment.service.impl.PaymentStateServiceImpl(repository, mapper, outbox)));
        target = new PaymentServiceImpl(repository, registry,
                mapper, currentUser, transactions, outbox, callbacks);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(new TestTransactionManager(),
                new AnnotationTransactionAttributeSource()));
        service = (PaymentService) proxy.getProxy();
        payment = Payment.builder().orderId("order-1").userId("user-1")
                .method(PaymentMethod.ONLINE).provider(PaymentProvider.PAYOS)
                .status(PaymentStatus.PENDING).amount(new BigDecimal("450000")).currency("VND").build();
        payment.setId("00000000-0000-0000-0000-000000000123");
        when(repository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(currentUser.getCurrentUserId()).thenReturn("user-1");
        when(registry.get(PaymentProvider.PAYOS)).thenReturn(handler);
    }

    @Test
    void reconciliationConfirmsProviderPaymentWithoutHoldingTransactionDuringQuery() {
        payment.setMerchantReference("123");
        payment.setPaymentUrl("https://pay.payos.vn/web/link-1");
        payment.setProviderAmount(new BigDecimal("450000"));
        payment.setProviderCurrency("VND");
        when(repository.findForReconciliation(any(), any(), any())).thenReturn(java.util.List.of(payment));
        when(repository.findByMerchantReference("123")).thenReturn(Optional.of(payment));
        when(handler.queryPayment(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return com.fashionstore.payment.dto.PaymentCallbackResult.builder().signatureValid(true)
                    .merchantReference("123").providerTransactionId("bank-transfer-1")
                    .amount(new BigDecimal("450000")).currency("VND").status(PaymentStatus.COMPLETED).build();
        });
        target.reconcilePendingPayments();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getTransactionId()).isEqualTo("bank-transfer-1");
    }

    @Test
    void reconciliationAppliesTerminalProviderStatusBeforeTryingToRecoverMissingUrl() {
        payment.setStatus(PaymentStatus.INITIATION_UNKNOWN);
        payment.setMerchantReference("123");
        payment.setProviderAmount(new BigDecimal("450000"));
        payment.setProviderCurrency("VND");
        when(repository.findForReconciliation(any(), any(), any())).thenReturn(java.util.List.of(payment));
        when(repository.findByMerchantReference("123")).thenReturn(Optional.of(payment));
        when(handler.initiate(any(), any())).thenThrow(
                new com.fashionstore.common.exception.AppException(com.fashionstore.payment.exception.PaymentErrorCode.PAYMENT_STATUS_INVALID));
        when(handler.queryPayment(any())).thenReturn(com.fashionstore.payment.dto.PaymentCallbackResult.builder()
                .signatureValid(true).merchantReference("123").amount(new BigDecimal("450000"))
                .currency("VND").status(PaymentStatus.FAILED).failureReason("Provider link expired").build());
        target.reconcilePendingPayments();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Provider link expired");
    }

    @Test
    void providerTimeoutKeepsUnknownInitiationInsteadOfFailingPayment() {
        when(handler.initiate(any(), anyString())).thenThrow(new RuntimeException("timeout"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.initiate(payment.getId(), "127.0.0.1"))
                .isInstanceOf(RuntimeException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.INITIATION_UNKNOWN);
        assertThat(payment.getMerchantReference()).isEqualTo("0");
    }

    @Test
    void concurrentInitiationCannotStartAnotherProviderRequest() {
        when(handler.initiate(any(), anyString())).thenAnswer(call -> {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.initiate(payment.getId(), "127.0.0.1"))
                    .isInstanceOf(com.fashionstore.common.exception.AppException.class)
                    .extracting("errorCode").isEqualTo(com.fashionstore.payment.exception.PaymentErrorCode.PAYMENT_INITIATION_IN_PROGRESS);
            return link();
        });
        service.initiate(payment.getId(), "127.0.0.1");
        verify(handler, times(1)).initiate(any(), anyString());
    }

    @Test
    void providerRequestRunsAfterDatabaseTransactionEnds() {
        when(handler.initiate(any(), anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(payment.getMerchantReference()).isNotBlank();
            return link();
        });
        assertThat(service.initiate(payment.getId(), "127.0.0.1").getPaymentUrl())
                .isEqualTo("https://pay.payos.vn/link-1");
    }

    @Test
    void repeatedInitiationReturnsStoredUrlWithoutCreatingAnotherProviderLink() {
        when(handler.initiate(any(), anyString())).thenReturn(link());
        PaymentInitiationResult first = service.initiate(payment.getId(), "127.0.0.1");
        PaymentInitiationResult second = service.initiate(payment.getId(), "127.0.0.1");
        assertThat(second.getPaymentUrl()).isEqualTo(first.getPaymentUrl());
        verify(handler, times(1)).initiate(any(), anyString());
    }

    @Test
    void callbackArrivingDuringInitiationKeepsCapturedTransactionId() {
        when(handler.initiate(any(), anyString())).thenAnswer(call -> {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setTransactionId("bank-transfer-1");
            return link();
        });
        service.initiate(payment.getId(), "127.0.0.1");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getTransactionId()).isEqualTo("bank-transfer-1");
    }

    private PaymentInitiationResult link() {
        return PaymentInitiationResult.builder().paymentUrl("https://pay.payos.vn/link-1")
                .providerTransactionId("123").providerAmount(new BigDecimal("450000"))
                .providerCurrency("VND").build();
    }

    static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected boolean isExistingTransaction(Object transaction) {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
