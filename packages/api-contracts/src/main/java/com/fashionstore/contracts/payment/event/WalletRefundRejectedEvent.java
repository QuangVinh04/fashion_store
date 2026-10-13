package com.fashionstore.contracts.payment.event;
public record WalletRefundRejectedEvent(String refundId,String orderId,String paymentId,String code,String reason) {}
