package com.fashionstore.order.promotion.service;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.order.promotion.dto.AvailablePromotionResponse;
import com.fashionstore.order.promotion.dto.PromotionItemDto;
import com.fashionstore.order.promotion.dto.PromotionPreviewResponse;
import com.fashionstore.order.promotion.dto.PromotionRequest;
import com.fashionstore.order.promotion.dto.PromotionResponse;
import com.fashionstore.order.promotion.dto.PromotionTarget;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface PromotionService {

    /** Discount a PRODUCT coupon takes off the goods total; zero for a blank code. */
    BigDecimal previewDiscount(String code, String userId, BigDecimal subtotal, List<PromotionItemDto> items);

    /** Discount a SHIPPING coupon takes off the shipping fee; zero for a blank code. */
    BigDecimal previewShippingDiscount(String code, String userId, BigDecimal subtotal, BigDecimal shippingFee);

    /** Reserves a PRODUCT coupon for the order and returns the discount recomputed under the promotion lock. */
    BigDecimal reserve(String code, String userId, String orderId, BigDecimal subtotal, List<PromotionItemDto> items);

    /** Reserves a SHIPPING coupon for the order and returns the discount recomputed under the promotion lock. */
    BigDecimal reserveShipping(String code, String userId, String orderId, BigDecimal subtotal, BigDecimal shippingFee);

    void confirm(String orderId);

    void release(String orderId);

    PromotionResponse createPromotion(PromotionRequest request);

    PromotionResponse updatePromotion(String id, PromotionRequest request);

    PromotionResponse getPromotionById(String id);

    PromotionResponse getPromotionByCode(String code);

    PageResponse<List<PromotionResponse>> getPromotions(Boolean active, Pageable pageable);

    void togglePromotionActive(String id, boolean active);

    PromotionPreviewResponse validatePromotionForCustomer(
            String code, String userId, BigDecimal subtotal, List<PromotionItemDto> items);

    List<AvailablePromotionResponse> listAvailable(
            String userId, PromotionTarget target, BigDecimal subtotal, List<PromotionItemDto> items);
}
