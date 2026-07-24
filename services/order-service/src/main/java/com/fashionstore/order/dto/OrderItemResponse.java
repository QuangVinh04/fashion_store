package com.fashionstore.order.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderItemResponse {
    String variantId;
    String productName;
    String size;
    String color;
    BigDecimal unitPrice;
    Integer quantity;
    BigDecimal lineTotal;
}
