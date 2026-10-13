package com.fashionstore.contracts.payment.event;
public record OrderFundsRefundRejectedEvent(String refundId,String orderId,String paymentId,String reasonType,
        String sourceId,String code,String reason) {}
