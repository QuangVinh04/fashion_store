package com.fashionstore.order.promotion.dto;

import com.fashionstore.order.promotion.entity.PromotionScopeType;
import com.fashionstore.order.promotion.entity.PromotionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A public coupon as shown to a customer. {@code eligible=false} carries the reason the current cart cannot use it;
 * {@code estimatedDiscount} is only filled for PRODUCT coupons (a SHIPPING discount depends on the shipping fee).
 */
public record AvailablePromotionResponse(
        String code,
        String description,
        PromotionTarget target,
        PromotionType type,
        BigDecimal value,
        BigDecimal maxDiscount,
        BigDecimal minOrderValue,
        LocalDateTime endAt,
        PromotionScopeType scopeType,
        boolean eligible,
        Integer reasonCode,
        String reason,
        BigDecimal estimatedDiscount) {
}
