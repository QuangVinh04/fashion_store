package com.fashionstore.order.dto;
public record CodCollectionResponse(String orderId,String paymentId,String operationId,String status,String amount,String currency,java.time.Instant collectedAt,
        String confirmedBy,java.time.Instant confirmedAt,String failureReason,String carrierRemittanceStatus,long version,java.util.List<String> evidenceIds) {}
