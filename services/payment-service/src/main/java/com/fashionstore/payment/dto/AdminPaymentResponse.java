package com.fashionstore.payment.dto;
import com.fashionstore.payment.entity.Payment;
import java.time.*;

public record AdminPaymentResponse(String id,String orderId,String userId,String method,String provider,
        String status,String amount,String currency,String merchantReference,String transactionId,
        String failureReason,Instant paidAt,Instant deliveredAt,Instant codCollectedAt,int activeGeneration) {
    public static AdminPaymentResponse from(Payment p) {
        return new AdminPaymentResponse(p.getId(),p.getOrderId(),p.getUserId(),p.getMethod().name(),p.getProvider().name(),
                p.getStatus().name(),p.getAmount().toPlainString(),p.getCurrency(),p.getMerchantReference(),p.getTransactionId(),
                p.getFailureReason(),p.getPaidAt()==null?null:p.getPaidAt().atZone(ZoneId.systemDefault()).toInstant(),
                p.getDeliveryRecordedAt(),
                p.getMethod() == com.fashionstore.common.payment.PaymentMethod.COD && p.getPaidAt() != null
                        ? p.getPaidAt().atZone(ZoneId.systemDefault()).toInstant() : null,
                0);
    }
}
