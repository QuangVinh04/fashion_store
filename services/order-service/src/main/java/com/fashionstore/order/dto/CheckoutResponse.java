package com.fashionstore.order.dto;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.order.model.enumeration.CheckoutStatus;
import com.fashionstore.order.model.enumeration.ShippingMethod;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckoutResponse {
    String id;
    String orderId;
    CheckoutStatus status;
    PaymentMethod paymentMethod;
    PaymentProvider paymentProvider;
    ShippingMethod shippingMethod;
    String couponCode;
    List<CheckoutItemResponse> items;
    BigDecimal subtotalAmount;
    BigDecimal discountAmount;
    BigDecimal shippingFee;
    BigDecimal totalAmount;
    LocalDateTime submittedAt;
}
