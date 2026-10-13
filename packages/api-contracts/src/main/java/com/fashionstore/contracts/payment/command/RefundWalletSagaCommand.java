package com.fashionstore.contracts.payment.command;

import java.math.BigDecimal;

public record RefundWalletSagaCommand(String compensationId,String orderId,String paymentId,int generation,
        BigDecimal amount,String currency,String reason) {}
