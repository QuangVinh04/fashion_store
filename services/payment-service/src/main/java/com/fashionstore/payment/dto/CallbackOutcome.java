package com.fashionstore.payment.dto;

/** Kết quả tổng quát của việc xử lý 1 callback/webhook, không phụ thuộc định dạng wire riêng của provider. */
public enum CallbackOutcome {
    INVALID_REQUEST,
    SIGNATURE_INVALID,
    PAYMENT_NOT_FOUND,
    AMOUNT_INVALID,
    ALREADY_PROCESSED,
    APPLIED
}
