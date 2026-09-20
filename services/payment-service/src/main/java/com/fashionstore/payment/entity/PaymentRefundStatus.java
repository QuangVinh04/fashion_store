package com.fashionstore.payment.entity;

/** Represents the provider-facing lifecycle of a refund attempt. */
public enum PaymentRefundStatus {
    PENDING,
    COMPLETED,
    FAILED
}
