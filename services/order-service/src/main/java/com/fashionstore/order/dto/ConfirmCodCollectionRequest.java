package com.fashionstore.order.dto;
public record ConfirmCodCollectionRequest(String operationId,java.math.BigDecimal receivedAmount,java.time.Instant receivedAt,java.util.List<String> evidenceIds) {}
