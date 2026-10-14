package com.fashionstore.payment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import vn.payos.PayOS;
import vn.payos.exception.InvalidSignatureException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.webhooks.WebhookData;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;
import vn.payos.service.blocking.webhooks.WebhooksService;

import java.math.BigDecimal;

class PayOSPaymentHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void forgedCancelParameterDoesNotCancelPendingProviderPayment() {
        PayOS client = mock(PayOS.class);
        PaymentRequestsService requests = mock(PaymentRequestsService.class);
        when(client.paymentRequests()).thenReturn(requests);
        when(requests.get(123L))
                .thenReturn(
                        vn.payos
                                .model
                                .v2
                                .paymentRequests
                                .PaymentLink
                                .builder()
                                .id("link-1")
                                .orderCode(123L)
                                .amount(450000L)
                                .amountPaid(0L)
                                .amountRemaining(450000L)
                                .status(vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PENDING)
                                .createdAt("2026-10-08T12:00:00Z")
                                .transactions(java.util.List.of())
                                .build());
        PaymentCallbackResult result =
                new PayOSPaymentHandler(client, objectMapper)
                        .verifyCallback(
                                java.util.Map.of("orderCode", "123", "cancel", "true"), null);
        assertThat(result.isSignatureValid()).isTrue();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        org.mockito.Mockito.verify(requests, org.mockito.Mockito.never())
                .cancel(any(Long.class), any(String.class));
    }

    @Test
    void providerLookupFailureCannotConfirmBrowserCancellation() {
        PayOS client = mock(PayOS.class);
        PaymentRequestsService requests = mock(PaymentRequestsService.class);
        when(client.paymentRequests()).thenReturn(requests);
        when(requests.get(123L))
                .thenThrow(new vn.payos.exception.ConnectionTimeoutException("timeout"));
        assertThatThrownBy(
                        () ->
                                new PayOSPaymentHandler(client, objectMapper)
                                        .verifyCallback(
                                                java.util.Map.of(
                                                        "orderCode", "123", "cancel", "true"),
                                                null))
                .isInstanceOf(AppException.class);
    }

    @Test
    void creationTimeoutRecoversExistingProviderLink() {
        PayOS client = mock(PayOS.class);
        PaymentRequestsService requests = mock(PaymentRequestsService.class);
        when(client.paymentRequests()).thenReturn(requests);
        when(requests.create(any()))
                .thenThrow(new vn.payos.exception.ConnectionTimeoutException("lost response"));
        when(requests.get(123L))
                .thenReturn(
                        vn.payos
                                .model
                                .v2
                                .paymentRequests
                                .PaymentLink
                                .builder()
                                .id("link-1")
                                .orderCode(123L)
                                .amount(450000L)
                                .amountPaid(0L)
                                .amountRemaining(450000L)
                                .status(vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PENDING)
                                .createdAt("2026-10-08T12:00:00Z")
                                .transactions(java.util.List.of())
                                .build());
        PayOSPaymentHandler handler = new PayOSPaymentHandler(client, objectMapper);
        ReflectionTestUtils.setField(handler, "returnUrl", "https://shop.test/success");
        ReflectionTestUtils.setField(handler, "cancelUrl", "https://shop.test/cancel");
        Payment payment =
                Payment.builder()
                        .orderId("order-1")
                        .merchantReference("123")
                        .amount(new BigDecimal("450000"))
                        .currency("VND")
                        .build();
        assertThat(handler.initiate(payment, "127.0.0.1").getPaymentUrl())
                .isEqualTo("https://pay.payos.vn/web/link-1");
    }

    @Test
    void initiateCreatesCheckoutLinkWithDeterministicOrderCode() {
        PayOS client = mock(PayOS.class);
        PaymentRequestsService paymentRequestsService = mock(PaymentRequestsService.class);
        when(client.paymentRequests()).thenReturn(paymentRequestsService);
        CreatePaymentLinkResponse response =
                CreatePaymentLinkResponse.builder()
                        .bin("970422")
                        .accountNumber("0123456789")
                        .accountName("FASHION STORE")
                        .checkoutUrl("https://pay.payos.vn/web/abc123")
                        .qrCode("qr-data")
                        .amount(450000L)
                        .description("Thanh toan order-1")
                        .orderCode(123L)
                        .currency("VND")
                        .paymentLinkId("link-1")
                        .status(vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PENDING)
                        .expiredAt(0L)
                        .build();
        when(paymentRequestsService.create(any())).thenReturn(response);

        PayOSPaymentHandler handler = new PayOSPaymentHandler(client, objectMapper);
        ReflectionTestUtils.setField(handler, "returnUrl", "https://shop.test/success");
        ReflectionTestUtils.setField(handler, "cancelUrl", "https://shop.test/cancel");

        Payment payment =
                Payment.builder()
                        .orderId("order-1")
                        .userId("user-1")
                        .method(PaymentMethod.ONLINE)
                        .provider(PaymentProvider.PAYOS)
                        .status(PaymentStatus.PENDING)
                        .amount(new BigDecimal("450000"))
                        .currency("VND")
                        .merchantReference("0123456789abcdef0123456789abcdef")
                        .build();

        PaymentInitiationResult result = handler.initiate(payment, "127.0.0.1");

        assertThat(result.getPaymentUrl()).isEqualTo("https://pay.payos.vn/web/abc123");
        assertThat(result.getProviderTransactionId()).isNotBlank();
        // Cùng một merchantReference phải luôn suy ra cùng một orderCode (idempotent).
        PaymentInitiationResult again = handler.initiate(payment, "127.0.0.1");
        assertThat(again.getProviderTransactionId()).isEqualTo(result.getProviderTransactionId());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void verifyCallbackUsesSignedDataEvenWhenOuterSuccessFlagChanges(boolean outerSuccess)
            throws Exception {
        PayOS client = mock(PayOS.class);
        WebhooksService webhooksService = mock(WebhooksService.class);
        when(client.webhooks()).thenReturn(webhooksService);
        WebhookData data =
                WebhookData.builder()
                        .orderCode(123L)
                        .amount(450000L)
                        .description("Thanh toan order-1")
                        .accountNumber("0123456789")
                        .reference("FT2609211030")
                        .transactionDateTime("2026-09-21 10:30:00")
                        .currency(outerSuccess ? "VND" : "USD")
                        .paymentLinkId("link-1")
                        .code("00")
                        .desc("success")
                        .counterAccountBankId("")
                        .counterAccountBankName("")
                        .counterAccountName("")
                        .counterAccountNumber("")
                        .virtualAccountName("")
                        .virtualAccountNumber("")
                        .build();
        when(webhooksService.verify(any())).thenReturn(data);

        PayOSPaymentHandler handler = new PayOSPaymentHandler(client, objectMapper);
        String rawBody =
                """
{"code":"00","desc":"success","success":%s,"signature":"sig","data":{"orderCode":123,"amount":450000}}
"""
                        .formatted(outerSuccess);

        PaymentCallbackResult result = handler.verifyCallback(java.util.Map.of(), rawBody);

        assertThat(result.isSignatureValid()).isTrue();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getMerchantReference()).isEqualTo("123");
        assertThat(result.getAmount()).isEqualByComparingTo("450000");
        assertThat(result.getCurrency()).isEqualTo(data.getCurrency());
    }

    @Test
    void verifyCallbackRejectsInvalidSignature() {
        PayOS client = mock(PayOS.class);
        WebhooksService webhooksService = mock(WebhooksService.class);
        when(client.webhooks()).thenReturn(webhooksService);
        when(webhooksService.verify(any()))
                .thenThrow(new InvalidSignatureException("bad signature"));

        PayOSPaymentHandler handler = new PayOSPaymentHandler(client, objectMapper);
        String rawBody =
                """
{"code":"00","desc":"success","success":true,"signature":"forged","data":{"orderCode":123}}
""";

        PaymentCallbackResult result = handler.verifyCallback(java.util.Map.of(), rawBody);

        assertThat(result.isSignatureValid()).isFalse();
    }

    @Test
    void queriedBankTransactionsKeepIndividualReferencesAndAmounts() throws Exception {
        PayOS client = mock(PayOS.class);
        PaymentRequestsService requests = mock(PaymentRequestsService.class);
        when(client.paymentRequests()).thenReturn(requests);
        var a =
                vn.payos
                        .model
                        .v2
                        .paymentRequests
                        .Transaction
                        .builder()
                        .reference("bank-a")
                        .amount(50000L)
                        .accountNumber("test")
                        .description("test")
                        .transactionDateTime("2026-10-11 10:00:00")
                        .build();
        var b =
                vn.payos
                        .model
                        .v2
                        .paymentRequests
                        .Transaction
                        .builder()
                        .reference("bank-b")
                        .amount(30000L)
                        .accountNumber("test")
                        .description("test")
                        .transactionDateTime("2026-10-11 10:01:00")
                        .build();
        when(requests.get(123L))
                .thenReturn(
                        vn.payos
                                .model
                                .v2
                                .paymentRequests
                                .PaymentLink
                                .builder()
                                .id("link")
                                .orderCode(123L)
                                .amount(80000L)
                                .amountPaid(80000L)
                                .amountRemaining(0L)
                                .status(vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PAID)
                                .createdAt("test")
                                .transactions(java.util.List.of(a, b))
                                .build());
        var result =
                new PayOSPaymentHandler(client, objectMapper)
                        .queryPayment(Payment.builder().merchantReference("123").build());
        var receipts = result.getTransactionReceipts();
        assertThat(receipts).hasSize(2);
        assertThat(receipts.get(1).providerTransactionId()).isEqualTo("bank-b");
        assertThat(receipts.get(1).amount()).isEqualTo(BigDecimal.valueOf(30000));
    }
}
