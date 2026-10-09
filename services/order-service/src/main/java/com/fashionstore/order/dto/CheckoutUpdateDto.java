package com.fashionstore.order.dto;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.order.entity.ShippingAddress;
import com.fashionstore.order.entity.enumeration.ShippingMethod;

import java.math.BigDecimal;

public record CheckoutUpdateDto (
        String addressId,
        ShippingAddress shippingAddress,
        PaymentMethod paymentMethod,
        PaymentProvider paymentProvider,
        ShippingMethod shippingMethod,
        String couponCode,
        BigDecimal discount,
        BigDecimal shippingFee,
        BigDecimal total
){}
