package com.fashionstore.payment.dto;

import jakarta.validation.constraints.Min;

public record ConfirmWalletPaymentRequest(@Min(0) int attemptGeneration) {}
