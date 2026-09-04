package com.fashionstore.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateOrderRequest {

    @NotBlank
    String recipientName;

    @NotBlank
    String recipientPhone;

    @NotBlank
    String shippingAddress;
}
