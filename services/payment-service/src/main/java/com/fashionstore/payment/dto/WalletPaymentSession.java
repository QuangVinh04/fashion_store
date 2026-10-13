package com.fashionstore.payment.dto;

import java.time.Instant;

public record WalletPaymentSession(String paymentId,String orderId,int attemptGeneration,String status,
        String currency,String amount,String balance,Instant expiresAt,boolean canConfirm,boolean canChangeMethod) {}
