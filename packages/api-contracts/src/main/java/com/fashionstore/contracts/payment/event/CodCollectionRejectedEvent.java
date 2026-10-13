package com.fashionstore.contracts.payment.event;
public record CodCollectionRejectedEvent(String operationId,String orderId,String paymentId,String code,String reason) {}
