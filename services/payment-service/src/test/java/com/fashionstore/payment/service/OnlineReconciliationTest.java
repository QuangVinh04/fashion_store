package com.fashionstore.payment.service;

import com.fashionstore.payment.client.PaymentHandler;
import com.fashionstore.payment.client.PaymentHandlerRegistry;
import com.fashionstore.common.payment.*;
import com.fashionstore.payment.dto.*;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fashionstore.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@ExtendWith(MockitoExtension.class)
class OnlineReconciliationTest {
    @Mock PaymentTransactionService paymentTransactionService;
    @Mock PaymentHandlerRegistry paymentHandlerRegistry;
    @Mock PaymentHandler handler;
    @Mock CallbackPaymentService callbackPaymentService;
    @InjectMocks PaymentServiceImpl service;
    @Test void cancelledOnlineReferenceIsQueriedWithoutReinitiatingIt() {
        var p=Payment.builder().method(PaymentMethod.ONLINE).provider(PaymentProvider.PAYOS).status(PaymentStatus.CANCELLED)
                .merchantReference("123").build();p.setId("pay");
        when(paymentTransactionService.findForReconciliation(any(),any(),any())).thenReturn(List.of(p));
        when(paymentTransactionService.lockAndPrepareForReconciliation(any(),any(),any())).thenReturn(p);
        when(paymentHandlerRegistry.get(PaymentProvider.PAYOS)).thenReturn(handler);
        when(handler.queryPayment(p)).thenReturn(PaymentCallbackResult.builder().signatureValid(true).status(PaymentStatus.PENDING).build());
        service.reconcilePendingPayments();
        verify(paymentTransactionService).findForReconciliation(argThat(statuses->statuses.contains(PaymentStatus.CANCELLED)&&statuses.contains(PaymentStatus.FAILED)),any(),any());
        verify(handler).queryPayment(p);verify(handler,never()).initiate(any(),any());
    }
    @Test void reconciliationOnlyQueriesLinksAndNeverCreatesThem() {
        var p=Payment.builder().method(PaymentMethod.ONLINE).provider(PaymentProvider.PAYOS).status(PaymentStatus.PENDING)
                .merchantReference("123").paymentUrl("https://pay").build();p.setId("pay");
        when(paymentTransactionService.findForReconciliation(any(),any(),any())).thenReturn(List.of(p));
        when(paymentTransactionService.lockAndPrepareForReconciliation(any(),any(),any())).thenReturn(p);
        when(paymentHandlerRegistry.get(PaymentProvider.PAYOS)).thenReturn(handler);
        var result=PaymentCallbackResult.builder().signatureValid(true).status(PaymentStatus.PENDING).build();
        when(handler.queryPayment(p)).thenReturn(result);
        service.reconcilePendingPayments();
        verify(paymentTransactionService).findForReconciliation(argThat(statuses->!statuses.contains(PaymentStatus.INITIATING)
                &&!statuses.contains(PaymentStatus.INITIATION_UNKNOWN)),any(),any());
        verify(callbackPaymentService).applyVerifiedResult(PaymentProvider.PAYOS,result);
        verify(handler,never()).initiate(any(),any());
    }
    @Test void codAndWalletNeverEnterExternalReconciliationEvenWithStaleCandidates() {
        var cod=Payment.builder().method(PaymentMethod.COD).provider(PaymentProvider.COD).status(PaymentStatus.COD_PENDING).build();
        var wallet=Payment.builder().method(PaymentMethod.WALLET).provider(PaymentProvider.INTERNAL).status(PaymentStatus.PENDING).build();
        when(paymentTransactionService.findForReconciliation(any(),any(),any())).thenReturn(List.of(cod,wallet));
        service.reconcilePendingPayments();verifyNoInteractions(paymentHandlerRegistry);
        verify(paymentTransactionService,never()).lockAndPrepareForReconciliation(any(),any(),any());
    }
}
