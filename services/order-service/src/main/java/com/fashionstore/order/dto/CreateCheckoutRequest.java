package com.fashionstore.order.dto;

import com.fashionstore.common.payment.PaymentMethod;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.order.model.enumeration.ShippingMethod;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateCheckoutRequest {

    String couponCode;

    @NotNull
    PaymentMethod paymentMethod;

    PaymentProvider paymentProvider;

    @Builder.Default
    ShippingMethod shippingMethod = ShippingMethod.STANDARD;
}
