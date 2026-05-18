package com.fashionstore.clothes_retail_api.modules.cart.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartItemResponse {
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
