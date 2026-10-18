package com.fashionstore.payment.dto;

public record PaymentReturnResolutionResponse(
        String orderId,
        String paymentId,
        int attemptGeneration,
        String status,
        String captureStatus
) {}
