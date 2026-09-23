package com.fashionstore.payment.service.provider;

import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.dto.PaymentRefundResult;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.PaymentRefund;

import java.util.Map;

/**
 * Một chiến lược duy nhất cho mỗi cổng thanh toán online (VNPay, PayOS, ...). COD không implement
 * interface này — nó không bao giờ đi qua registry (xem {@code PaymentRequestedEventListener}).
 */
public interface PaymentHandler {

    PaymentProvider provider();

    /** Khởi tạo giao dịch, trả về URL/QR để khách thanh toán. */
    PaymentInitiationResult initiate(Payment payment, String clientIp);

    /**
     * Xác minh callback từ cổng thanh toán. {@code queryParams} dành cho callback dạng query-string
     * (VNPay), {@code rawBody} dành cho callback dạng JSON body (PayOS) — mỗi handler chỉ dùng đúng một
     * trong hai, tham số còn lại có thể rỗng/null.
     */
    PaymentCallbackResult verifyCallback(Map<String, String> queryParams, String rawBody);

    /** Yêu cầu hoàn tiền. Provider không hỗ trợ hoàn tiền qua API thì ném AppException. */
    PaymentRefundResult refund(Payment payment, PaymentRefund refund);
}
