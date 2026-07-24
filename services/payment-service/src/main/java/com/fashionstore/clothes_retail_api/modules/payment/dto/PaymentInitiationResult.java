package com.fashionstore.product.modules.payment.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PaymentInitiationResult {
    String paymentUrl;
    String merchantReference;
    String providerTransactionId;
    BigDecimal providerAmount;
    String providerCurrency;
}
