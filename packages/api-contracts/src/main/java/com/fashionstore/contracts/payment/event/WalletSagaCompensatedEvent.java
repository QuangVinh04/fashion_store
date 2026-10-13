package com.fashionstore.contracts.payment.event;

public record WalletSagaCompensatedEvent(String compensationId,String orderId,String paymentId,int generation,String ledgerEntryId) {}
