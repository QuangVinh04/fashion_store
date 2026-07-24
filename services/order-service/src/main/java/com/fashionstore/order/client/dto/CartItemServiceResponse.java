package com.fashionstore.order.client.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CartItemServiceResponse {
    String id;
    String variantId;
    String productId;
    String productName;
    String size;
    String color;
    Integer quantity;
    BigDecimal unitPrice;
    BigDecimal totalPrice;
}
