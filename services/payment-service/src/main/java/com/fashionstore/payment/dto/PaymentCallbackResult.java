package com.fashionstore.payment.dto;

import com.fashionstore.payment.entity.enumeration.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder(toBuilder = true)
public class PaymentCallbackResult {
    public record ProviderReceipt(String providerTransactionId,BigDecimal amount,String currency) {}
    java.util.List<ProviderReceipt> transactionReceipts;
    String merchantReference;
    String providerTransactionId;
    PaymentStatus status;
    String failureReason;
    BigDecimal amount;
    String currency;
    boolean signatureValid;
    String orderId;
    String paymentId;
}
