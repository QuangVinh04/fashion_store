package com.fashionstore.order.checkout.mapper;

import com.fashionstore.order.cart.dto.CartItemResponse;
import com.fashionstore.order.cart.entity.CartItem;
import com.fashionstore.order.checkout.entity.CheckoutItem;
import com.fashionstore.order.promotion.dto.PromotionItemDto;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** Single place that turns cart/checkout lines into the items a promotion is evaluated against. */
public final class PromotionItemMapper {

    private static final String CATEGORY_SEPARATOR = ",";

    private PromotionItemMapper() {
    }

    public static PromotionItemDto fromCartItem(CartItem item, List<String> categoryIds, BigDecimal lineTotal) {
        return PromotionItemDto.builder()
                .variantId(item.getVariantId())
                .productId(item.getProductId())
                .categoryIds(categoryIds)
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .lineTotal(lineTotal)
                .build();
    }

    public static PromotionItemDto fromCartItemResponse(CartItemResponse item, List<String> categoryIds) {
        return PromotionItemDto.builder()
                .variantId(item.getVariantId())
                .productId(item.getProductId())
                .categoryIds(categoryIds)
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .lineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .build();
    }

    public static PromotionItemDto fromCheckoutItem(CheckoutItem item) {
        return PromotionItemDto.builder()
                .variantId(item.getVariantId())
                .productId(item.getProductId())
                .categoryIds(splitCategoryIds(item.getCategoryIds()))
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .lineTotal(item.getLineTotal())
                .build();
    }

    public static String joinCategoryIds(List<String> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return null;
        }
        return String.join(CATEGORY_SEPARATOR, categoryIds);
    }

    private static List<String> splitCategoryIds(String categoryIds) {
        if (categoryIds == null || categoryIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(categoryIds.split(CATEGORY_SEPARATOR)).map(String::trim).toList();
    }
}
