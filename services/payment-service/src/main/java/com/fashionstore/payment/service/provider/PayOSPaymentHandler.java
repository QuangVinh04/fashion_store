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

import org.springframework.util.StringUtils;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

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

    private static final int ORDER_CODE_HEX_LENGTH = 13;
    private static final long MAX_PAYOS_ORDER_CODE = 9007199254740991L;

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
        String cleanOrderId = payment.getOrderId().replace("-", "");
        String description = ("Thanh toan " + cleanOrderId);
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
        if (rawBody != null && !rawBody.isBlank()) {
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

        // Xử lý return/cancel callback từ PayOS khi chuyển hướng người dùng về trang web
        if (queryParams != null && (queryParams.containsKey("orderCode") || queryParams.containsKey("id"))) {
            String orderCodeStr = queryParams.get("orderCode");
            String statusParam = queryParams.get("status");
            boolean isCancel = "true".equalsIgnoreCase(queryParams.get("cancel"))
                    || "CANCELLED".equalsIgnoreCase(statusParam);

            Long orderCode = null;
            if (StringUtils.hasText(orderCodeStr)) {
                try {
                    orderCode = Long.parseLong(orderCodeStr.trim());
                } catch (NumberFormatException ignored) {
                }
            }

            if (orderCode != null) {
                try {
                    PaymentLink paymentLink = payOSClient.paymentRequests().get(orderCode);
                    boolean isPaid = paymentLink.getStatus() == PaymentLinkStatus.PAID;
                    boolean isLinkCancelled = paymentLink.getStatus() == PaymentLinkStatus.CANCELLED;

                    if (!isPaid && (isCancel || isLinkCancelled)) {
                        if (!isLinkCancelled && paymentLink.getStatus() == PaymentLinkStatus.PENDING) {
                            try {
                                payOSClient.paymentRequests().cancel(orderCode, "Khách hàng hủy thanh toán");
                            } catch (Exception ex) {
                                log.warn("[PayOS] Không thể hủy link trên PayOS cho orderCode={}", orderCode, ex);
                            }
                        }
                        return PaymentCallbackResult.builder()
                                .merchantReference(String.valueOf(orderCode))
                                .providerTransactionId(paymentLink.getId())
                                .amount(paymentLink.getAmount() == null ? null : BigDecimal.valueOf(paymentLink.getAmount()))
                                .currency("VND")
                                .status(PaymentStatus.FAILED)
                                .failureReason("Khách hàng đã hủy thanh toán trên cổng PayOS")
                                .signatureValid(true)
                                .build();
                    }

                    if (isPaid) {
                        String ref = (paymentLink.getTransactions() != null && !paymentLink.getTransactions().isEmpty())
                                ? paymentLink.getTransactions().get(0).getReference()
                                : paymentLink.getId();
                        return PaymentCallbackResult.builder()
                                .merchantReference(String.valueOf(orderCode))
                                .providerTransactionId(ref)
                                .amount(paymentLink.getAmount() == null ? null : BigDecimal.valueOf(paymentLink.getAmount()))
                                .currency("VND")
                                .status(PaymentStatus.COMPLETED)
                                .signatureValid(true)
                                .build();
                    }

                    if (isCancel) {
                        return PaymentCallbackResult.builder()
                                .merchantReference(String.valueOf(orderCode))
                                .providerTransactionId(paymentLink.getId())
                                .amount(paymentLink.getAmount() == null ? null : BigDecimal.valueOf(paymentLink.getAmount()))
                                .currency("VND")
                                .status(PaymentStatus.FAILED)
                                .failureReason("Khách hàng đã hủy giao dịch PayOS")
                                .signatureValid(true)
                                .build();
                    }
                } catch (Exception ex) {
                    log.error("[PayOS] Không thể tra cứu PaymentLink từ PayOS API: {}", ex.getMessage());
                    if (isCancel) {
                        return PaymentCallbackResult.builder()
                                .merchantReference(String.valueOf(orderCode))
                                .currency("VND")
                                .status(PaymentStatus.FAILED)
                                .failureReason("Khách hàng đã hủy thanh toán PayOS")
                                .signatureValid(true)
                                .build();
                    }
                }
            }
        }

        return PaymentCallbackResult.builder()
                .signatureValid(false)
                .failureReason("PayOS request payload hoặc params không hợp lệ")
                .build();
    }

    @Override
    public PaymentRefundResult refund(Payment payment, PaymentRefund refund) {
        throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_UNSUPPORTED,
                "PayOS không có API hoàn tiền — giao dịch chuyển khoản đã hoàn tất phải hoàn thủ công");
    }

    private long toOrderCode(String merchantReference) {
        String clean = merchantReference.replace("-", "");
        String hex = clean.length() > ORDER_CODE_HEX_LENGTH
                ? clean.substring(0, ORDER_CODE_HEX_LENGTH)
                : clean;
        long code = Long.parseLong(hex, 16);
        return Math.min(code, MAX_PAYOS_ORDER_CODE);
    }
}
