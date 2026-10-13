package com.fashionstore.payment.dto;
import com.fashionstore.payment.entity.PaymentRefund;
import java.time.*;
public record AdminRefundResponse(String id,String businessRefundId,String paymentId,String orderId,String destination,
        String reasonType,String amount,String currency,String status,boolean budgetReserved,String ledgerEntryId,
        String providerRefundId,String failureReason,Instant createdAt,Instant completedAt) {
    public static AdminRefundResponse from(PaymentRefund r) {
        return new AdminRefundResponse(r.getId(),r.getBusinessRefundId(),r.getPayment().getId(),r.getOrderId(),r.getDestination(),
                r.getReasonType(),r.getAmount().toPlainString(),r.getCurrency(),r.getStatus().name(),r.isBudgetReserved(),
                r.getLedgerEntryId(),r.getProviderRefundId(),r.getFailureReason(),instant(r.getCreatedAt()),instant(r.getCompletedAt()));
    }
    private static Instant instant(LocalDateTime value) { return value==null?null:value.atZone(ZoneId.systemDefault()).toInstant(); }
}
