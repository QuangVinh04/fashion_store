package com.fashionstore.order.cart.dto.cart;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartResponse {
    String id;
    String userId;
    List<CartItemResponse> items;
    // Tổng tiền tính
    BigDecimal totalPrice;
    Integer totalQuantity;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
