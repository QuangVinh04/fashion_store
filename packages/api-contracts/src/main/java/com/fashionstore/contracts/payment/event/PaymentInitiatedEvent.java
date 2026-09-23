package com.fashionstore.contracts.payment.event;

/** Đã có URL/QR thanh toán, chưa xác nhận tiền — chỉ để FE hiển thị, không đổi bước saga. */
public record PaymentInitiatedEvent(
        String orderId,
        String paymentId,
        String paymentUrl
) {
}
