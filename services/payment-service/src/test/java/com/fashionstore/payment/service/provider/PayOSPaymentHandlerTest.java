package com.fashionstore.payment.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.payos.PayOS;
import vn.payos.exception.InvalidSignatureException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.webhooks.WebhookData;
import vn.payos.service.blocking.v2.paymentRequests.PaymentRequestsService;
import vn.payos.service.blocking.webhooks.WebhooksService;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayOSPaymentHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void initiateCreatesCheckoutLinkWithDeterministicOrderCode() {
        PayOS client = mock(PayOS.class);
        PaymentRequestsService paymentRequestsService = mock(PaymentRequestsService.class);
        when(client.paymentRequests()).thenReturn(paymentRequestsService);
        CreatePaymentLinkResponse response = CreatePaymentLinkResponse.builder()
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

        Payment payment = Payment.builder()
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

    @Test
    void verifyCallbackAppliesSuccessfulWebhook() throws Exception {
        PayOS client = mock(PayOS.class);
        WebhooksService webhooksService = mock(WebhooksService.class);
        when(client.webhooks()).thenReturn(webhooksService);
        WebhookData data = WebhookData.builder()
                .orderCode(123L)
                .amount(450000L)
                .description("Thanh toan order-1")
                .accountNumber("0123456789")
                .reference("FT2609211030")
                .transactionDateTime("2026-09-21 10:30:00")
                .currency("VND")
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
        String rawBody = """
                {"code":"00","desc":"success","success":true,"signature":"sig","data":{"orderCode":123,"amount":450000}}
                """;

        PaymentCallbackResult result = handler.verifyCallback(java.util.Map.of(), rawBody);

        assertThat(result.isSignatureValid()).isTrue();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getMerchantReference()).isEqualTo("123");
        assertThat(result.getAmount()).isEqualByComparingTo("450000");
    }

    @Test
    void verifyCallbackRejectsInvalidSignature() {
        PayOS client = mock(PayOS.class);
        WebhooksService webhooksService = mock(WebhooksService.class);
        when(client.webhooks()).thenReturn(webhooksService);
        when(webhooksService.verify(any())).thenThrow(new InvalidSignatureException("bad signature"));

        PayOSPaymentHandler handler = new PayOSPaymentHandler(client, objectMapper);
        String rawBody = """
                {"code":"00","desc":"success","success":true,"signature":"forged","data":{"orderCode":123}}
                """;

        PaymentCallbackResult result = handler.verifyCallback(java.util.Map.of(), rawBody);

        assertThat(result.isSignatureValid()).isFalse();
    }

    @Test
    void refundIsUnsupported() {
        PayOSPaymentHandler handler = new PayOSPaymentHandler(mock(PayOS.class), objectMapper);

        assertThatThrownBy(() -> handler.refund(mock(Payment.class), mock(com.fashionstore.payment.entity.PaymentRefund.class)))
                .isInstanceOf(AppException.class);
    }
}
