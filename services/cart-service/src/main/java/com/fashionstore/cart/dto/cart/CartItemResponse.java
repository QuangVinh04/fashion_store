package com.fashionstore.cart.dto.cart;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    Integer quantity;
    BigDecimal unitPrice;
    BigDecimal totalPrice;

    String productName;
    String size;
    String color;
    String sku;
    Boolean available;      // tồn kho còn không
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
