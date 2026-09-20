package com.fashionstore.payment.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.dto.PaymentRefundResult;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.PaymentRefund;
import com.fashionstore.payment.entity.PaymentRefundStatus;
import com.fashionstore.payment.entity.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaypalPaymentGatewayTest {

    @Test
    void refundUsesCaptureIdAndIdempotencyKey() throws Exception {
        PaypalFeignClient client = mock(PaypalFeignClient.class);
        when(client.getAccessToken(anyString(), any(MultiValueMap.class)))
                .thenReturn(new ObjectMapper().readTree("{\"access_token\":\"token-1\"}"));
        when(client.refundCapture(anyString(), anyString(), anyString(), any()))
                .thenReturn(new ObjectMapper().readTree("{\"id\":\"refund-1\",\"status\":\"COMPLETED\"}"));
        PaypalPaymentGateway gateway = new PaypalPaymentGateway(
                client, "client", "secret", "USD", new BigDecimal("25000"),
                "https://shop.test/success", "https://shop.test/cancel"
        );
        Payment payment = payment(PaymentProvider.PAYPAL, "capture-1");
        PaymentRefund refund = refund(payment, "refund-message-1");

        PaymentRefundResult result = gateway.refund(payment, refund);

        assertThat(result.status()).isEqualTo(PaymentRefundStatus.COMPLETED);
        assertThat(result.providerRefundId()).isEqualTo("refund-1");
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(client).refundCapture(
                org.mockito.ArgumentMatchers.eq("capture-1"),
                org.mockito.ArgumentMatchers.eq("Bearer token-1"),
                org.mockito.ArgumentMatchers.eq("refund-message-1"),
                body.capture()
        );
        assertThat(((Map<?, ?>) body.getValue()).containsKey("amount")).isTrue();
    }

    private Payment payment(PaymentProvider provider, String transactionId) {
        return Payment.builder()
                .orderId("order-1")
                .userId("user-1")
                .method(PaymentMethod.ONLINE)
                .provider(provider)
                .status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("500000"))
                .currency("VND")
                .transactionId(transactionId)
                .merchantReference("merchant-1")
                .build();
    }

    private PaymentRefund refund(Payment payment, String key) {
        return PaymentRefund.builder()
                .payment(payment)
                .orderId(payment.getOrderId())
                .amount(new BigDecimal("250000"))
                .provider(payment.getProvider())
                .idempotencyKey(key)
                .reason("Customer return")
                .build();
    }
}
