package com.fashionstore.contracts.payment.event;
public record CodCollectionConfirmedEvent(String operationId,String orderId,String paymentId,java.time.Instant collectedAt) {}
