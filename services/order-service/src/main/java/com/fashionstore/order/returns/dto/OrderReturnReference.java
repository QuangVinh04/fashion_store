package com.fashionstore.order.returns.dto;

import com.fashionstore.order.returns.entity.ReturnRequestKind;
import com.fashionstore.order.returns.entity.ReturnRequestStatus;
import com.fashionstore.order.returns.mapper.ReturnRequestMapper;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderReturnReference(String orderId, String returnRequestId,
        ReturnRequestStatus returnStatus, ReturnRequestKind kind,
        BigDecimal approvedRefundAmount, String ledgerEntryId, String failureReason,
        Instant startedAt, Instant closedAt) {
    public ReturnRefundStatus refundStatus() {
        return ReturnRequestMapper.refundStatus(
                returnStatus, ledgerEntryId, failureReason, startedAt, closedAt);
    }

    public BigDecimal actualRefundAmount() {
        return refundStatus() == ReturnRefundStatus.COMPLETED ? approvedRefundAmount : null;
    }
}
