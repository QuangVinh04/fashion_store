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
    void queryVerifiesProviderSignatureAndNormalizesAmount() throws Exception {
        VnPayFeignClient client = mock(VnPayFeignClient.class);
        com.fasterxml.jackson.databind.node.ObjectNode response = queryResponse("00", "00");
        when(client.query(any())).thenReturn(response);
        Payment payment = Payment.builder().orderId("order-1").merchantReference("merchant-1")
                .amount(new BigDecimal("500000")).providerTransactionDate("20261008110000").build();
        PaymentCallbackResult result = new VnPayPaymentHandler(properties(), client).queryPayment(payment);
        assertThat(result.isSignatureValid()).isTrue();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getAmount()).isEqualByComparingTo("500000");
        ArgumentCaptor<Map<String, String>> request = ArgumentCaptor.forClass(Map.class);
        verify(client).query(request.capture());
        assertThat(request.getValue()).containsEntry("vnp_Command", "querydr")
                .containsEntry("vnp_TxnRef", "merchant-1")
                .containsEntry("vnp_TransactionDate", "20261008110000");
    }

    @Test
    void queryApiErrorIsNotInterpretedAsPaymentFailure() throws Exception {
        VnPayFeignClient client = mock(VnPayFeignClient.class);
        when(client.query(any())).thenReturn(queryResponse("91", ""));
        Payment payment = Payment.builder().orderId("order-1").merchantReference("merchant-1")
                .providerTransactionDate("20261008110000").build();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new VnPayPaymentHandler(properties(), client).queryPayment(payment))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
    }

    @Test
    void queryRejectsTamperedProviderResponse() throws Exception {
        VnPayFeignClient client = mock(VnPayFeignClient.class);
        com.fasterxml.jackson.databind.node.ObjectNode response = queryResponse("00", "00");
        response.put("vnp_SecureHash", "forged");
        when(client.query(any())).thenReturn(response);
        Payment payment = Payment.builder().orderId("order-1").merchantReference("merchant-1")
                .providerTransactionDate("20261008110000").build();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new VnPayPaymentHandler(properties(), client).queryPayment(payment))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode queryResponse(String code, String status) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("vnp_ResponseId", "response-1").put("vnp_Command", "querydr").put("vnp_ResponseCode", code)
                .put("vnp_Message", "Found").put("vnp_TmnCode", "tmn-code").put("vnp_TxnRef", "merchant-1")
                .put("vnp_Amount", "50000000").put("vnp_BankCode", "NCB").put("vnp_PayDate", "20261008120000")
                .put("vnp_TransactionNo", "123456").put("vnp_TransactionType", "01")
                .put("vnp_TransactionStatus", status).put("vnp_OrderInfo", "Thanh toan order-1")
                .put("vnp_PromotionCode", "").put("vnp_PromotionAmount", "");
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA512");
        mac.init(new javax.crypto.spec.SecretKeySpec("secret".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA512"));
        String signed = "response-1|querydr|" + code + "|Found|tmn-code|merchant-1|50000000|NCB|20261008120000|123456|01|"
                + status + "|Thanh toan order-1||";
        node.put("vnp_SecureHash", java.util.HexFormat.of().formatHex(mac.doFinal(signed.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        return node;
    }

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
