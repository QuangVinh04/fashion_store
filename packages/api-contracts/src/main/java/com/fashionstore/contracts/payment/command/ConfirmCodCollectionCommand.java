package com.fashionstore.contracts.payment.command;
public record ConfirmCodCollectionCommand(String operationId,String orderId,String paymentId,java.math.BigDecimal amount,String currency,java.time.Instant collectedAt,java.util.List<String> evidenceIds,String confirmedBy) {}
