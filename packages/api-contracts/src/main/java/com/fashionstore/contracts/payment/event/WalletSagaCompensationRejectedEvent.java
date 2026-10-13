package com.fashionstore.contracts.payment.event;

public record WalletSagaCompensationRejectedEvent(String compensationId,String orderId,String paymentId,String code,String reason) {}
