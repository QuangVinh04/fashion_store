package com.fashionstore.payment.client;

import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.entity.Payment;

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
     * (VNPay), {@code rawBody} dành cho callback dạng JSON body (PayOS) — mỗi handler chỉ dùng đúng
     * một trong hai, tham số còn lại có thể rỗng/null.
     */
    PaymentCallbackResult verifyCallback(Map<String, String> queryParams, String rawBody);

    /** Tra cứu từ server provider; phục vụ local và phục hồi khi callback bị mất. */
    PaymentCallbackResult queryPayment(Payment payment);

    /**
     * Đóng link đang mở sau khi payment bị huỷ, để khách không trả tiền được nữa. Provider không có
     * API huỷ (VNPay) thì link tự hết hạn theo deadline.
     */
    default void cancelLink(Payment payment) {}
}
