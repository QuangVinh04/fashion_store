package com.fashionstore.payment.gateway;

import com.fashionstore.payment.dto.PaymentRefundResult;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.PaymentRefund;

/** Contract implemented by gateways that can refund a captured payment. */
public interface RefundablePaymentGateway extends PaymentGateway {

    /** Requests an idempotent refund from the external provider. */
    PaymentRefundResult refund(Payment payment, PaymentRefund refund);
}
