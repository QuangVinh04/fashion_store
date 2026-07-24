package com.fashionstore.order.dto;

import com.fashionstore.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateOrderStatusRequest {

    @NotNull
    OrderStatus status;

    String shippingProvider;

    String trackingCode;
}
