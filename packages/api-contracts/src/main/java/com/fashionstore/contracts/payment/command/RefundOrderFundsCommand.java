package com.fashionstore.contracts.payment.command;
import java.math.BigDecimal;
public record RefundOrderFundsCommand(String refundId,String orderId,String paymentId,BigDecimal amount,
        String currency,String reasonType,String sourceId) {}
