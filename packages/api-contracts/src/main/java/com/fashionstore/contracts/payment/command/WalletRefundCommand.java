package com.fashionstore.contracts.payment.command;
public record WalletRefundCommand(String refundId,String orderId,String paymentId,String userId,java.math.BigDecimal amount,String currency,String reason) {}
