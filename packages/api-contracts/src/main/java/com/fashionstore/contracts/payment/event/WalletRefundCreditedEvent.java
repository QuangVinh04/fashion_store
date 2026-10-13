package com.fashionstore.contracts.payment.event;
public record WalletRefundCreditedEvent(String refundId,String orderId,String paymentId,String ledgerEntryId,java.math.BigDecimal amount) {}
