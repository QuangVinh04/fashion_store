package com.fashionstore.contracts.payment.event;
import java.math.BigDecimal;
public record OrderFundsRefundCreditedEvent(String refundId,String orderId,String paymentId,String reasonType,
        String sourceId,String ledgerEntryId,BigDecimal amount) {}
