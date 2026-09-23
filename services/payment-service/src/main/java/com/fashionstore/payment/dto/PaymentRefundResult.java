package com.fashionstore.payment.dto;

import com.fashionstore.payment.entity.enumeration.PaymentRefundStatus;

/** Result returned by a payment provider after a refund request. */
public record PaymentRefundResult(
        String providerRefundId,
        PaymentRefundStatus status,
        String failureReason
) {
    /** Creates a successful provider refund result. */
    public static PaymentRefundResult completed(String providerRefundId) {
        return new PaymentRefundResult(providerRefundId, PaymentRefundStatus.COMPLETED, null);
    }

    /** Creates a provider result that is still processing. */
    public static PaymentRefundResult pending(String providerRefundId) {
        return new PaymentRefundResult(providerRefundId, PaymentRefundStatus.PENDING, null);
    }

    /** Creates a failed provider refund result. */
    public static PaymentRefundResult failed(String providerRefundId, String failureReason) {
        return new PaymentRefundResult(providerRefundId, PaymentRefundStatus.FAILED, failureReason);
    }
}
