package com.fashionstore.order.promotion.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.order.promotion.dto.PromotionItemDto;
import com.fashionstore.order.promotion.entity.Promotion;
import com.fashionstore.order.promotion.entity.PromotionScopeType;
import com.fashionstore.order.promotion.dto.PromotionTarget;
import com.fashionstore.order.promotion.entity.PromotionType;
import com.fashionstore.order.shared.exception.OrderErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Pure money math of a coupon: how much it takes off the goods total or the shipping fee. */
final class PromotionCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private PromotionCalculator() {
    }

    static BigDecimal discountFor(
            Promotion promotion, BigDecimal subtotal, List<PromotionItemDto> items, BigDecimal shippingFee) {
        if (promotion.getTarget() == PromotionTarget.SHIPPING) {
            return shippingDiscount(promotion, shippingFee);
        }
        return productDiscount(promotion, subtotal, items);
    }

    static BigDecimal productDiscount(Promotion promotion, BigDecimal subtotal, List<PromotionItemDto> items) {
        return discountOn(promotion, applicableAmount(promotion, subtotal, items));
    }

    static BigDecimal shippingDiscount(Promotion promotion, BigDecimal shippingFee) {
        return discountOn(promotion, shippingFee == null ? BigDecimal.ZERO : shippingFee);
    }

    private static BigDecimal discountOn(Promotion promotion, BigDecimal base) {
        BigDecimal discount = promotion.getValue();
        if (promotion.getType() == PromotionType.PERCENT) {
            discount = base.multiply(promotion.getValue()).divide(ONE_HUNDRED, 0, RoundingMode.HALF_UP);
            if (promotion.getMaxDiscount() != null) {
                discount = discount.min(promotion.getMaxDiscount());
            }
        }
        return discount.min(base).max(BigDecimal.ZERO);
    }

    private static BigDecimal applicableAmount(Promotion promotion, BigDecimal subtotal, List<PromotionItemDto> items) {
        BigDecimal wholeOrder = subtotal != null ? subtotal : BigDecimal.ZERO;
        boolean hasScope = promotion.getScopeIds() != null && !promotion.getScopeIds().isBlank();
        if (promotion.getScopeType() == PromotionScopeType.ALL || !hasScope) {
            return wholeOrder;
        }
        if (items == null || items.isEmpty()) {
            throw new AppException(OrderErrorCode.PROMOTION_SCOPE_MISMATCH);
        }
        Set<String> scopeIds = Arrays.stream(promotion.getScopeIds().split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toSet());
        List<PromotionItemDto> matchingItems = items.stream()
                .filter(item -> isInScope(promotion.getScopeType(), scopeIds, item))
                .toList();
        if (matchingItems.isEmpty()) {
            throw new AppException(OrderErrorCode.PROMOTION_SCOPE_MISMATCH);
        }
        return matchingItems.stream()
                .map(item -> item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean isInScope(PromotionScopeType scopeType, Set<String> scopeIds, PromotionItemDto item) {
        if (scopeType == PromotionScopeType.PRODUCT) {
            return (item.getProductId() != null && scopeIds.contains(item.getProductId()))
                    || (item.getVariantId() != null && scopeIds.contains(item.getVariantId()));
        }
        return item.getCategoryIds() != null && item.getCategoryIds().stream().anyMatch(scopeIds::contains);
    }
}
