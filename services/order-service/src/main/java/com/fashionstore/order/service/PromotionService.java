package com.fashionstore.order.service;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.order.dto.PromotionItemDto;
import com.fashionstore.order.dto.PromotionRequest;
import com.fashionstore.order.dto.PromotionResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface PromotionService {

    BigDecimal previewDiscount(String code, String userId, BigDecimal subtotal, List<PromotionItemDto> items);

    void reserve(String code, String userId, String orderId, BigDecimal subtotal, List<PromotionItemDto> items);

    void confirm(String orderId);

    void release(String orderId);

    PromotionResponse createPromotion(PromotionRequest request);

    PromotionResponse updatePromotion(String id, PromotionRequest request);

    PromotionResponse getPromotionById(String id);

    PromotionResponse getPromotionByCode(String code);

    PageResponse<List<PromotionResponse>> getPromotions(Boolean active, Pageable pageable);

    void togglePromotionActive(String id, boolean active);

    com.fashionstore.order.dto.PromotionPreviewResponse validatePromotionForCustomer(String code, BigDecimal subtotal);
}
