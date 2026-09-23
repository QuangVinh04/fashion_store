package com.fashionstore.payment.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.config.payment.VnPayConfig;
import com.fashionstore.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.dto.PaymentRefundResult;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.PaymentRefund;
import com.fashionstore.payment.entity.enumeration.PaymentRefundStatus;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fashionstore.payment.gateway.VnPayFeignClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VnPayPaymentHandlerTest {

    @Test
    void refundBuildsSignedVnPayRequest() throws Exception {
        VnPayFeignClient client = mock(VnPayFeignClient.class);
        when(client.refund(any())).thenReturn(new ObjectMapper().readTree("""
                {"vnp_ResponseCode":"00","vnp_TransactionStatus":"00","vnp_TransactionNo":"refund-1"}
                """));
        VnPayPaymentHandler handler = new VnPayPaymentHandler(properties(), client);
        Payment payment = Payment.builder()
                .orderId("order-1")
                .userId("user-1")
                .method(PaymentMethod.ONLINE)
                .provider(PaymentProvider.VNPAY)
                .status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("500000"))
                .currency("VND")
                .transactionId("transaction-1")
                .merchantReference("merchant-1")
                .providerTransactionDate("20260921103000")
                .build();
        PaymentRefund refund = PaymentRefund.builder()
                .payment(payment)
                .orderId("order-1")
                .amount(new BigDecimal("500000"))
                .provider(PaymentProvider.VNPAY)
                .idempotencyKey("refund-message-1")
                .build();

        PaymentRefundResult result = handler.refund(payment, refund);

        assertThat(result.status()).isEqualTo(PaymentRefundStatus.COMPLETED);
        ArgumentCaptor<Map<String, String>> request = ArgumentCaptor.forClass(Map.class);
        verify(client).refund(request.capture());
        assertThat(request.getValue())
                .containsEntry("vnp_Command", "refund")
                .containsEntry("vnp_TransactionType", "02")
                .containsEntry("vnp_TransactionDate", "20260921103000")
                .containsKey("vnp_SecureHash");
    }

    @Test
    void verifyCallbackRejectsTamperedSignature() {
        VnPayFeignClient client = mock(VnPayFeignClient.class);
        VnPayPaymentHandler handler = new VnPayPaymentHandler(properties(), client);
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("vnp_TxnRef", "merchant-1");
        payload.put("vnp_ResponseCode", "00");
        payload.put("vnp_TransactionStatus", "00");
        payload.put("vnp_TransactionNo", "txn-1");
        payload.put("vnp_Amount", "50000000");
        payload.put("vnp_SecureHash", "not-a-real-hash");

        PaymentCallbackResult result = handler.verifyCallback(payload, null);

        assertThat(result.isSignatureValid()).isFalse();
    }

    private VnPayConfig properties() {
        VnPayConfig properties = new VnPayConfig();
        properties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setApiUrl("https://sandbox.vnpayment.vn");
        properties.setTmnCode("tmn-code");
        properties.setHashSecret("secret");
        properties.setReturnUrl("https://shop.test/return");
        properties.setCreateBy("fashion-store");
        properties.setServerIp("127.0.0.1");
        return properties;
    }
}
