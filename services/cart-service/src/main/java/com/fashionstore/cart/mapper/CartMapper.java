package com.fashionstore.cart.mapper;


import com.fashionstore.cart.dto.cart.CartItemResponse;
import com.fashionstore.cart.dto.cart.CartResponse;
import com.fashionstore.cart.model.Cart;
import com.fashionstore.cart.model.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Mapper(componentModel = "spring")
public interface CartMapper {

    default CartResponse toCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems() == null
                ? List.of()
                : cart.getItems().stream()
                .map(this::toCartItemResponse)
                .toList();

        Integer totalQuantity = itemResponses.stream()
                .map(CartItemResponse::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        BigDecimal totalPrice = itemResponses.stream()
                .map(CartItemResponse::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .id(cart.getId())
                .userId(cart.getUserId())
                .items(itemResponses)
                .totalQuantity(totalQuantity)
                .totalPrice(totalPrice)
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    @Mapping(target = "totalPrice", expression = "java(calculateTotalPrice(item))")
    CartItemResponse toCartItemResponse(CartItem item);

    default BigDecimal calculateTotalPrice(CartItem item) {
        if (item == null || item.getUnitPrice() == null || item.getQuantity() == null) {
            return BigDecimal.ZERO;
        }

        return item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }
}
