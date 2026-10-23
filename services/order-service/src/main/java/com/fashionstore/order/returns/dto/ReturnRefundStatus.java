package com.fashionstore.order.returns.dto;

/** Read-only refund progress for return details; not a persisted workflow state. */
public enum ReturnRefundStatus {
    NOT_STARTED,
    PROCESSING,
    COMPLETED,
    /** Error, uncertain outcome or legacy data requiring reconciliation. */
    REVIEW_REQUIRED
}
