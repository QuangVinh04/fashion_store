package com.fashionstore.payment.payment.service.impl;

import com.fashionstore.payment.shared.outbox.OutboxEventRecorder;
import com.fashionstore.payment.payment.service.PaymentTransactionService;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.payment.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.payment.entity.Payment;
import com.fashionstore.payment.payment.entity.PaymentStatus;
import com.fashionstore.payment.payment.mapper.PaymentResponseMapper;
import com.fashionstore.payment.payment.repository.PaymentRepository;
import com.fashionstore.payment.payment.service.PaymentService;
import com.fashionstore.payment.payment.client.PaymentHandler;
import com.fashionstore.payment.payment.client.PaymentHandlerRegistry;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.payment.payment.dto.PaymentReturnResolutionResponse;
import com.fashionstore.payment.payment.exception.PaymentErrorCode;

class PaymentServiceImplTest {
    PaymentRepository repository = mock(PaymentRepository.class);
    PaymentHandlerRegistry registry = mock(PaymentHandlerRegistry.class);
    PaymentHandler handler = mock(PaymentHandler.class);
    CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    PaymentService service;
    PaymentServiceImpl target;
    Payment payment;

    OutboxEventRecorder outbox = mock(OutboxEventRecorder.class);

    @BeforeEach
    void setUp() {
        PaymentResponseMapper mapper = mock(PaymentResponseMapper.class);
        var jdbcTemplate = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(5000000000000000L);
        var databaseTarget = new com.fashionstore.payment.payment.service.PaymentTransactionService(repository, jdbcTemplate, null, outbox,
                new PaymentStateServiceImpl(repository, mapper, outbox), mock(com.fashionstore.payment.wallet.service.WalletService.class),
                mock(com.fashionstore.payment.payment.service.WalletPaymentService.class));
        ProxyFactory databaseProxy = new ProxyFactory(databaseTarget);
        databaseProxy.addAdvice(new TransactionInterceptor(new TestTransactionManager(),
                new AnnotationTransactionAttributeSource()));
        var database = (com.fashionstore.payment.payment.service.PaymentTransactionService) databaseProxy.getProxy();
        com.fashionstore.payment.payment.service.CallbackPaymentService callbacks = new CallbackPaymentServiceImpl(
                registry, database);
        target = new PaymentServiceImpl(repository, registry,
                mapper, currentUser, database, callbacks, mock(com.fashionstore.payment.payment.service.WalletPaymentService.class));
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(new TestTransactionManager(),
                new AnnotationTransactionAttributeSource()));
        service = (PaymentService) proxy.getProxy();
        payment = Payment.builder().orderId("order-1").userId("user-1")
                .method(PaymentMethod.ONLINE).provider(PaymentProvider.PAYOS)
                .status(PaymentStatus.PENDING).amount(new BigDecimal("450000")).currency("VND")
                .merchantReference("123").authorizationExpiresAt(java.time.Instant.now().plusSeconds(900)).build();
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
        when(repository.findForReconciliation(any(), any(), any(), any())).thenReturn(java.util.List.of(payment));
        when(repository.findByMerchantReference("123")).thenReturn(Optional.of(payment));
        when(handler.queryPayment(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return com.fashionstore.payment.payment.dto.PaymentCallbackResult.builder().signatureValid(true)
                    .merchantReference("123").providerTransactionId("bank-transfer-1")
                    .amount(new BigDecimal("450000")).currency("VND").status(PaymentStatus.COMPLETED).build();
        });
        target.reconcilePendingPayments();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getTransactionId()).isEqualTo("bank-transfer-1");
    }

    @Test
    void providerFailureLeavesPaymentPendingAndReportsGatewayError() {
        when(handler.initiate(any(), anyString())).thenThrow(new RuntimeException("timeout"));
        assertThatThrownBy(() -> service.initiate(payment.getId(), "127.0.0.1"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaymentUrl()).isNull();
        verify(outbox, never()).saveMessage(any(), any(), any());
    }

    @Test
    void initiationStoresUrlAndPublishesItToTheSaga() {
        when(handler.initiate(any(), anyString())).thenAnswer(call -> {
            // Khoá dòng payment được giữ suốt lúc gọi provider để cancel/callback xếp hàng sau.
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return link();
        });
        assertThat(service.initiate(payment.getId(), "127.0.0.1").getPaymentUrl())
                .isEqualTo("https://pay.payos.vn/link-1");
        assertThat(payment.getPaymentUrl()).isEqualTo("https://pay.payos.vn/link-1");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(outbox).saveMessage(eq("order-1"),
                eq(com.fashionstore.contracts.common.EventTypes.PAYMENT_INITIATED), any());
    }

    @Test
    void cancelledPaymentCannotCreateAnotherLink() {
        payment.setStatus(PaymentStatus.CANCELLED);
        assertThatThrownBy(() -> service.initiate(payment.getId(), "127.0.0.1"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_STATUS_INVALID);
        verify(handler, never()).initiate(any(), any());
    }

    @Test
    void expiredAuthorizationCannotCreateLink() {
        payment.setAuthorizationExpiresAt(java.time.Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> service.initiate(payment.getId(), "127.0.0.1"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_STATUS_INVALID);
        verify(handler, never()).initiate(any(), any());
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
    void resolvesOwnedPaymentByMerchantReferenceSuccessfully() {
        Payment p = Payment.builder()
                .orderId("order-100")
                .userId("user-1")
                .provider(PaymentProvider.VNPAY)
                .status(PaymentStatus.COMPLETED)
                .initiationAttempts(1)
                .merchantReference("VNP-REF-123")
                .build();
        p.setId("pay-1");

        when(repository.findForReturnByMerchantReference("VNP-REF-123")).thenReturn(Optional.of(p));
        when(currentUser.getCurrentUserId()).thenReturn("user-1");

        PaymentReturnResolutionResponse resolution =
                service.resolveReturn(PaymentProvider.VNPAY, "VNP-REF-123");

        assertThat(resolution.orderId()).isEqualTo("order-100");
        assertThat(resolution.paymentId()).isEqualTo("pay-1");
        assertThat(resolution.attemptGeneration()).isEqualTo(1);
        assertThat(resolution.status()).isEqualTo("COMPLETED");
        assertThat(resolution.captureStatus()).isEqualTo("CAPTURED");
    }

    @Test
    void resolvesOwnedPaymentByTransactionIdFallbackSuccessfully() {
        Payment p = Payment.builder()
                .orderId("order-200")
                .userId("user-1")
                .provider(PaymentProvider.PAYOS)
                .status(PaymentStatus.PENDING)
                .initiationAttempts(2)
                .transactionId("PAYOS-TXN-456")
                .build();
        p.setId("pay-2");

        when(repository.findForReturnByMerchantReference("PAYOS-TXN-456")).thenReturn(Optional.empty());
        when(repository.findByTransactionId("PAYOS-TXN-456")).thenReturn(Optional.of(p));
        when(currentUser.getCurrentUserId()).thenReturn("user-1");

        PaymentReturnResolutionResponse resolution =
                service.resolveReturn(PaymentProvider.PAYOS, "PAYOS-TXN-456");

        assertThat(resolution.orderId()).isEqualTo("order-200");
        assertThat(resolution.paymentId()).isEqualTo("pay-2");
        assertThat(resolution.status()).isEqualTo("PENDING");
        assertThat(resolution.captureStatus()).isEqualTo("UNCONFIRMED");
    }

    @Test
    void preventsIdorWhenPaymentBelongsToAnotherUser() {
        Payment p = Payment.builder()
                .orderId("order-300")
                .userId("other-user")
                .provider(PaymentProvider.VNPAY)
                .status(PaymentStatus.COMPLETED)
                .merchantReference("REF-FORBIDDEN")
                .build();
        p.setId("pay-3");

        when(repository.findForReturnByMerchantReference("REF-FORBIDDEN")).thenReturn(Optional.of(p));
        when(currentUser.getCurrentUserId()).thenReturn("attacker-user");

        assertThatThrownBy(() -> service.resolveReturn(PaymentProvider.VNPAY, "REF-FORBIDDEN"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void throwsNotFoundWhenPaymentDoesNotExist() {
        when(repository.findForReturnByMerchantReference("NOT-FOUND")).thenReturn(Optional.empty());
        when(repository.findByTransactionId("NOT-FOUND")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveReturn(PaymentProvider.VNPAY, "NOT-FOUND"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    void throwsNotFoundForInvalidProviderOrBlankReference() {
        assertThatThrownBy(() -> service.resolveReturn(PaymentProvider.COD, "REF"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);

        assertThatThrownBy(() -> service.resolveReturn(PaymentProvider.VNPAY, "   "))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
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
        @Override protected void doSetRollbackOnly(DefaultTransactionStatus status) { }
    }
}
