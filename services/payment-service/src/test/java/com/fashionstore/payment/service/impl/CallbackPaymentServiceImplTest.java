package com.fashionstore.payment.service.impl;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.dto.CallbackOutcome;
import com.fashionstore.payment.dto.CallbackProcessResult;
import com.fashionstore.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.dto.PaymentResponse;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fashionstore.payment.repository.PaymentRepository;
import com.fashionstore.payment.service.PaymentStateService;
import com.fashionstore.payment.service.provider.PaymentHandler;
import com.fashionstore.payment.service.provider.PaymentHandlerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallbackPaymentServiceImplTest {

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    PaymentHandlerRegistry paymentHandlerRegistry;
    @Mock
    PaymentHandler paymentHandler;
    @Mock
    PaymentStateService paymentStateService;

    CallbackPaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CallbackPaymentServiceImpl(paymentRepository, paymentHandlerRegistry, paymentStateService);
    }

    @Test
    void emptyRequestIsRejectedBeforeTouchingHandler() {
        CallbackProcessResult result = service.processCallback(PaymentProvider.VNPAY, Map.of(), null);

        assertThat(result.outcome()).isEqualTo(CallbackOutcome.INVALID_REQUEST);
    }

    @Test
    void invalidSignatureStopsBeforeLookup() {
        when(paymentHandlerRegistry.get(PaymentProvider.VNPAY)).thenReturn(paymentHandler);
        when(paymentHandler.verifyCallback(any(), any())).thenReturn(
                PaymentCallbackResult.builder().signatureValid(false).build());

        CallbackProcessResult result = service.processCallback(PaymentProvider.VNPAY, Map.of("vnp_TxnRef", "x"), null);

        assertThat(result.outcome()).isEqualTo(CallbackOutcome.SIGNATURE_INVALID);
    }

    @Test
    void payosCallbackFallsBackToTransactionIdLookup() {
        Payment payment = pendingPayment(PaymentProvider.PAYOS);
        when(paymentHandlerRegistry.get(PaymentProvider.PAYOS)).thenReturn(paymentHandler);
        when(paymentHandler.verifyCallback(any(), any())).thenReturn(
                PaymentCallbackResult.builder()
                        .signatureValid(true)
                        .merchantReference("123")
                        .amount(new BigDecimal("450000"))
                        .currency("VND")
                        .status(PaymentStatus.COMPLETED)
                        .build());
        when(paymentRepository.findByMerchantReference("123")).thenReturn(Optional.empty());
        when(paymentRepository.findByTransactionId("123")).thenReturn(Optional.of(payment));
        when(paymentStateService.isProviderAmountValid(any(), any())).thenReturn(true);
        when(paymentStateService.applyResult(any(), any())).thenReturn(new PaymentResponse());

        CallbackProcessResult result = service.processCallback(PaymentProvider.PAYOS, Map.of(), "{}");

        assertThat(result.outcome()).isEqualTo(CallbackOutcome.APPLIED);
    }

    @Test
    void alreadyProcessedPaymentIsNotReappliedIdempotently() {
        Payment payment = pendingPayment(PaymentProvider.VNPAY);
        payment.setStatus(PaymentStatus.COMPLETED);
        when(paymentHandlerRegistry.get(PaymentProvider.VNPAY)).thenReturn(paymentHandler);
        when(paymentHandler.verifyCallback(any(), any())).thenReturn(
                PaymentCallbackResult.builder()
                        .signatureValid(true)
                        .merchantReference("merchant-1")
                        .status(PaymentStatus.COMPLETED)
                        .build());
        when(paymentRepository.findByMerchantReference("merchant-1")).thenReturn(Optional.of(payment));
        when(paymentStateService.isProviderAmountValid(any(), any())).thenReturn(true);

        CallbackProcessResult result = service.processCallback(PaymentProvider.VNPAY, Map.of("vnp_TxnRef", "merchant-1"), null);

        assertThat(result.outcome()).isEqualTo(CallbackOutcome.ALREADY_PROCESSED);
    }

    private Payment pendingPayment(PaymentProvider provider) {
        return Payment.builder()
                .orderId("order-1")
                .userId("user-1")
                .method(PaymentMethod.ONLINE)
                .provider(provider)
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("450000"))
                .currency("VND")
                .merchantReference("merchant-1")
                .providerAmount(new BigDecimal("450000"))
                .providerCurrency("VND")
                .build();
    }
}
