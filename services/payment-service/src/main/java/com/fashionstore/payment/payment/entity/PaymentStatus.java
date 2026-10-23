package com.fashionstore.payment.payment.entity;

public enum PaymentStatus {
    PENDING,
    INITIATING,
    INITIATION_UNKNOWN,
    COD_PENDING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REFUNDED
}
