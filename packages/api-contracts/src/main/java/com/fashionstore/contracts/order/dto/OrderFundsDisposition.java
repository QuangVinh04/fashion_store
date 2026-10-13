package com.fashionstore.contracts.order.dto;
import java.math.BigDecimal;
public record OrderFundsDisposition(String orderId,String userId,String paymentId,String status,
        BigDecimal amount,String currency,String inventoryReservationId) {}
