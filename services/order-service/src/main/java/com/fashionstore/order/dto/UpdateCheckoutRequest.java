package com.fashionstore.order.dto;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.order.model.enumeration.ShippingMethod;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateCheckoutRequest {
    PaymentMethod paymentMethod;
    PaymentProvider paymentProvider;
    ShippingMethod shippingMethod;
    String couponCode;
}
