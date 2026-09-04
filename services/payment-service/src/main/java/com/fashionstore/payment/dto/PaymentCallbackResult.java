package com.fashionstore.payment.dto;

import com.fashionstore.payment.entity.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PaymentCallbackResult {
    String merchantReference;
    String providerTransactionId;
    PaymentStatus status;
    String failureReason;
    BigDecimal amount;
    String currency;
    boolean signatureValid;
}
