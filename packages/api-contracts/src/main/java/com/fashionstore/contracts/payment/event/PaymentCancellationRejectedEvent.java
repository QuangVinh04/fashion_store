package com.fashionstore.contracts.payment.event;

public record PaymentCancellationRejectedEvent(
        String orderId,
        /** Payment đã bị thu tiền; null khi nhận message cũ chưa có trường này. */
        String paymentId,
        String failureCode,
        String failureMessage
) {
}
