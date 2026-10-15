package com.fashionstore.order.entity.enumeration;

public enum ReturnRequestStatus {
    REQUESTED,
    APPROVED,
    /** GHN đã lấy hàng tại khách: khóa hủy, chờ kho nhận. */
    IN_TRANSIT,
    RECEIVED,
    REFUNDED,
    REJECTED,
    CANCELLED,

    // Legacy values retained for database compatibility
    PENDING,
    RETURN_APPROVED,
    PICKUP_PENDING,
    PICKED_UP,
    QC_PASSED,
    DISPUTE,
    DISPOSITION_PENDING,
    REFUND_PENDING,
    RETURN_CANCELLED,
    LEGACY_REVIEW_REQUIRED
}
