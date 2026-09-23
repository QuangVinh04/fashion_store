package com.fashionstore.payment.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.dto.PaymentRefundResult;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.PaymentRefund;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import com.fashionstore.payment.exception.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.math.BigDecimal;
import java.util.Map;

/**
 * PayOS là chuyển khoản QR thời gian thực — không có bước "capture" (giống PayPal), xác nhận hoàn toàn
 * qua webhook (giống VNPay), và không có API hoàn tiền (giao dịch đã hoàn tất thì phải chuyển khoản
 * hoàn tiền thủ công — {@link #refund} luôn ném lỗi, {@code PaymentRequestedEventListener} đã có sẵn
 * catch chung để chuyển thành REFUND_FAILED thay vì phải xử lý riêng ở đây).
 *
 * <p>PayOS bắt buộc {@code orderCode} là số nguyên, trong khi {@code merchantReference} của hệ thống là
 * chuỗi hex 32 ký tự — {@link #toOrderCode} suy ra một mã số quyết định (deterministic) từ đó. Chiều
 * ngược lại (webhook trả về orderCode) không thể phục hồi lại chuỗi gốc, nên orderCode được lưu lại vào
 * cột {@code transactionId} (đã có sẵn, dùng chung cơ chế của {@code PaymentServiceImpl.initiate}) để
 * {@code CallbackPaymentServiceImpl} tra cứu lại đúng payment khi webhook gọi về.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayOSPaymentHandler implements PaymentHandler {

    private static final int ORDER_CODE_HEX_LENGTH = 15;

    private final PayOS payOSClient;
    private final ObjectMapper objectMapper;

    @Value("${payment.payos.return-url}")
    private String returnUrl;

    @Value("${payment.payos.cancel-url}")
    private String cancelUrl;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.PAYOS;
    }

    @Override
    public PaymentInitiationResult initiate(Payment payment, String clientIp) {
        long orderCode = toOrderCode(payment.getMerchantReference());
        String description = ("Thanh toan " + payment.getOrderId());
        description = description.length() > 25 ? description.substring(0, 25) : description;

        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(payment.getAmount().longValueExact())
                .description(description)
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .build();

        CreatePaymentLinkResponse response;
        try {
            response = payOSClient.paymentRequests().create(request);
        } catch (PayOSException exception) {
            throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR, exception);
        }

        return PaymentInitiationResult.builder()
                .paymentUrl(response.getCheckoutUrl())
                .merchantReference(payment.getMerchantReference())
                .providerTransactionId(String.valueOf(orderCode))
                .providerAmount(BigDecimal.valueOf(response.getAmount()))
                .providerCurrency("VND")
                .build();
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> queryParams, String rawBody) {
        try {
            Webhook webhook = objectMapper.readValue(rawBody, Webhook.class);
            WebhookData data = payOSClient.webhooks().verify(webhook);
            boolean successful = Boolean.TRUE.equals(webhook.getSuccess()) && "00".equals(webhook.getCode());

            return PaymentCallbackResult.builder()
                    .merchantReference(String.valueOf(data.getOrderCode()))
                    .providerTransactionId(data.getReference())
                    .amount(data.getAmount() == null ? null : BigDecimal.valueOf(data.getAmount()))
                    .currency("VND")
                    .status(successful ? PaymentStatus.COMPLETED : PaymentStatus.FAILED)
                    .failureReason(successful ? null : "PayOS webhook code: " + webhook.getCode())
                    .signatureValid(true)
                    .build();
        } catch (PayOSException exception) {
            log.warn("[PayOS] webhook signature verification failed", exception);
            return PaymentCallbackResult.builder()
                    .signatureValid(false)
                    .failureReason("PayOS signature invalid")
                    .build();
        } catch (Exception exception) {
            log.warn("[PayOS] cannot parse webhook body", exception);
            return PaymentCallbackResult.builder()
                    .signatureValid(false)
                    .failureReason("PayOS webhook payload invalid")
                    .build();
        }
    }

    @Override
    public PaymentRefundResult refund(Payment payment, PaymentRefund refund) {
        throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_UNSUPPORTED,
                "PayOS không có API hoàn tiền — giao dịch chuyển khoản đã hoàn tất phải hoàn thủ công");
    }

    private long toOrderCode(String merchantReference) {
        String hex = merchantReference.length() > ORDER_CODE_HEX_LENGTH
                ? merchantReference.substring(0, ORDER_CODE_HEX_LENGTH)
                : merchantReference;
        return Long.parseLong(hex, 16);
    }
}
