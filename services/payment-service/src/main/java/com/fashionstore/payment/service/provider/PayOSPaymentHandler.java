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
import vn.payos.exception.NotFoundException;
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
 * <p>Payment mới lưu orderCode dạng số ở merchantReference trước HTTP, nên webhook luôn tra được
 * payment kể cả khi response khởi tạo chưa về. Reference hex của payment cũ vẫn được hỗ trợ.
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
        // Payment mới lưu orderCode dạng số TRƯỚC HTTP; payment cũ vẫn hỗ trợ reference hex.
        long orderCode = toOrderCode(payment.getMerchantReference());
        if (payment.getInitiationAttempts() > 1) {
            // Lần trước có thể đã thành công nhưng response bị mất. Tra cứu trước khi thử tạo lại.
            try {
                return recoverExistingLink(payment, orderCode);
            } catch (NotFoundException notFound) {
                // Provider xác nhận chưa có link: được phép tạo, vẫn dùng cùng orderCode.
            } catch (PayOSException exception) {
                throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR, exception);
            }
        }
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
            try {
                return recoverExistingLink(payment, orderCode);
            } catch (PayOSException lookupFailure) {
                exception.addSuppressed(lookupFailure);
            }
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
                // SDK xác minh chữ ký của data; outer success/code không nằm trong chữ ký.
                boolean successful = "00".equals(data.getCode());

                return PaymentCallbackResult.builder()
                        .merchantReference(String.valueOf(data.getOrderCode()))
                        .providerTransactionId(data.getReference())
                        .amount(data.getAmount() == null ? null : BigDecimal.valueOf(data.getAmount()))
                        .currency("VND")
                        .status(successful ? PaymentStatus.COMPLETED : PaymentStatus.FAILED)
                        .failureReason(successful ? null : "PayOS webhook code: " + data.getCode())
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

        // Return/cancel URL là dữ liệu trình duyệt, không có chữ ký. Chỉ dùng orderCode
        // để tra cứu server; không tin status/cancel và không hủy link dựa trên chúng.
        if (queryParams != null && StringUtils.hasText(queryParams.get("orderCode"))) {
            String reference = queryParams.get("orderCode").trim();
            if (reference.matches("[0-9]{1,16}")) {
                return queryPayment(Payment.builder().merchantReference(reference).build());
            }
        }

        return PaymentCallbackResult.builder()
                .signatureValid(false)
                .failureReason("PayOS request payload hoặc params không hợp lệ")
                .build();
    }

    @Override
    public PaymentCallbackResult queryPayment(Payment payment) {
        long orderCode = toOrderCode(payment.getMerchantReference());
        PaymentLink link;
        try {
            link = payOSClient.paymentRequests().get(orderCode);
        } catch (PayOSException exception) {
            throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR, exception);
        }
        if (link == null || link.getOrderCode() == null || link.getOrderCode() != orderCode) {
            throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        boolean paid = link.getStatus() == PaymentLinkStatus.PAID;
        boolean failed = link.getStatus() == PaymentLinkStatus.CANCELLED || link.getStatus() == PaymentLinkStatus.EXPIRED;
        String transactionId = paid && link.getTransactions() != null && !link.getTransactions().isEmpty()
                ? link.getTransactions().get(0).getReference() : link.getId();
        Long amount = paid && link.getAmountPaid() != null ? link.getAmountPaid() : link.getAmount();
        return PaymentCallbackResult.builder().signatureValid(true).merchantReference(String.valueOf(orderCode))
                .providerTransactionId(transactionId).amount(amount == null ? null : BigDecimal.valueOf(amount))
                .currency("VND").status(paid ? PaymentStatus.COMPLETED : failed ? PaymentStatus.FAILED : PaymentStatus.PENDING)
                .failureReason(failed ? "PayOS link status: " + link.getStatus() : null).build();
    }

    /** Dùng lại link provider đã tạo, kể cả khi response khởi tạo trước đó bị mất. */
    private PaymentInitiationResult recoverExistingLink(Payment payment, long orderCode) {
        PaymentLink link = payOSClient.paymentRequests().get(orderCode);
        if (link == null || link.getOrderCode() == null || link.getOrderCode() != orderCode
                || link.getAmount() == null || payment.getAmount().compareTo(BigDecimal.valueOf(link.getAmount())) != 0) {
            throw new AppException(PaymentErrorCode.PAYMENT_AMOUNT_INVALID);
        }
        if (link.getStatus() == PaymentLinkStatus.CANCELLED || link.getStatus() == PaymentLinkStatus.EXPIRED) {
            throw new AppException(PaymentErrorCode.PAYMENT_STATUS_INVALID);
        }
        return PaymentInitiationResult.builder().paymentUrl("https://pay.payos.vn/web/" + link.getId())
                .merchantReference(payment.getMerchantReference()).providerTransactionId(String.valueOf(orderCode))
                .providerAmount(BigDecimal.valueOf(link.getAmount())).providerCurrency("VND").build();
    }

    @Override
    public PaymentRefundResult refund(Payment payment, PaymentRefund refund) {
        throw new AppException(PaymentErrorCode.PAYMENT_PROVIDER_UNSUPPORTED,
                "PayOS không có API hoàn tiền — giao dịch chuyển khoản đã hoàn tất phải hoàn thủ công");
    }

    private long toOrderCode(String merchantReference) {
        if (merchantReference.length() <= 16 && merchantReference.matches("[0-9]+")) {
            return Long.parseLong(merchantReference);
        }
        String clean = merchantReference.replace("-", "");
        String hex = clean.length() > ORDER_CODE_HEX_LENGTH
                ? clean.substring(0, ORDER_CODE_HEX_LENGTH)
                : clean;
        long code = Long.parseLong(hex, 16);
        return Math.min(code, MAX_PAYOS_ORDER_CODE);
    }
}
