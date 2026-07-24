package com.fashionstore.product.modules.order.event;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;

import java.math.BigDecimal;

@Deprecated(forRemoval = true)
public record OrderPaymentRequestedEvent(
        String orderId,
        String userId,
        PaymentMethod method,
        PaymentProvider provider,
        BigDecimal amount
) {
}
