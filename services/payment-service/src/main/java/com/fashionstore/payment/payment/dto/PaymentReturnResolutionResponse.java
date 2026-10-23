package com.fashionstore.payment.payment.dto;

public record PaymentReturnResolutionResponse(
        String orderId,
        String paymentId,
        int attemptGeneration,
        String status,
        String captureStatus
) {}
