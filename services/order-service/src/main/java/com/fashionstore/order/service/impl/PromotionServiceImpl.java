package com.fashionstore.order.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.order.dto.PromotionItemDto;
import com.fashionstore.order.dto.PromotionPreviewResponse;
import com.fashionstore.order.dto.PromotionRequest;
import com.fashionstore.order.dto.PromotionResponse;
import com.fashionstore.order.entity.CouponUsage;
import com.fashionstore.order.entity.Promotion;
import com.fashionstore.order.entity.enumeration.CouponUsageStatus;
import com.fashionstore.order.entity.enumeration.PromotionScopeType;
import com.fashionstore.order.entity.enumeration.PromotionType;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.repository.CouponUsageRepository;
import com.fashionstore.order.repository.PromotionRepository;
import com.fashionstore.order.service.PromotionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PromotionServiceImpl implements PromotionService {

    PromotionRepository promotionRepository;
    CouponUsageRepository couponUsageRepository;

    @Override
    @Transactional(readOnly = true)
    public BigDecimal previewDiscount(String code, String userId, BigDecimal subtotal, List<PromotionItemDto> items) {
        if (code == null || code.isBlank()) {
            return BigDecimal.ZERO;
        }
        Promotion promotion = findAndValidatePromotion(code.trim(), userId, subtotal, items);
        return calculateDiscount(promotion, subtotal, items);
    }

    @Override
    @Transactional
    public void reserve(String code, String userId, String orderId, BigDecimal subtotal, List<PromotionItemDto> items) {
        if (code == null || code.isBlank()) {
            return;
        }
        if (couponUsageRepository.findByOrderId(orderId).isPresent()) {
            return; // Idempotent: already reserved
        }

        // Khóa bi quan trên promotion để ngăn race condition vượt quota
        Promotion promotion = promotionRepository.findForUpdateByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new AppException(OrderErrorCode.PROMOTION_NOT_FOUND));

        validatePromotionEligibility(promotion, userId, subtotal, items);

        CouponUsage couponUsage = CouponUsage.builder()
                .promotion(promotion)
                .userId(userId)
                .orderId(orderId)
                .usedAt(LocalDateTime.now())
                .status(CouponUsageStatus.RESERVED)
                .build();

        couponUsageRepository.save(couponUsage);
        log.info("Reserved coupon {} for user {} order {}", promotion.getCode(), userId, orderId);
    }

    @Override
    @Transactional
    public void confirm(String orderId) {
        couponUsageRepository.findByOrderId(orderId).ifPresent(usage -> {
            if (usage.getStatus() == CouponUsageStatus.RESERVED) {
                usage.setStatus(CouponUsageStatus.CONFIRMED);
                couponUsageRepository.save(usage);
                log.info("Confirmed coupon usage for order {}", orderId);
            }
        });
    }

    @Override
    @Transactional
    public void release(String orderId) {
        couponUsageRepository.findByOrderId(orderId).ifPresent(usage -> {
            if (usage.getStatus() != CouponUsageStatus.RELEASED) {
                usage.setStatus(CouponUsageStatus.RELEASED);
                couponUsageRepository.save(usage);
                log.info("Released coupon usage for order {}", orderId);
            }
        });
    }

    @Override
    @Transactional
    public PromotionResponse createPromotion(PromotionRequest request) {
        validatePromotionRequest(request);

        if (promotionRepository.existsByCodeIgnoreCase(request.getCode().trim())) {
            throw new AppException(OrderErrorCode.PROMOTION_CODE_EXISTS);
        }

        Promotion promotion = Promotion.builder()
                .code(request.getCode().trim().toUpperCase())
                .type(request.getType())
                .value(request.getValue())
                .maxDiscount(request.getMaxDiscount())
                .minOrderValue(request.getMinOrderValue())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .totalQuota(request.getTotalQuota())
                .perUserQuota(request.getPerUserQuota())
                .scopeType(request.getScopeType())
                .scopeIds(request.getScopeIds())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        return toResponse(promotionRepository.save(promotion));
    }

    @Override
    @Transactional
    public PromotionResponse updatePromotion(String id, PromotionRequest request) {
        validatePromotionRequest(request);

        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new AppException(OrderErrorCode.PROMOTION_NOT_FOUND));

        String newCode = request.getCode().trim().toUpperCase();
        if (!promotion.getCode().equalsIgnoreCase(newCode)) {
            long usageCount = couponUsageRepository.countByPromotionIdAndStatusIn(
                    id, List.of(CouponUsageStatus.RESERVED, CouponUsageStatus.CONFIRMED));
            if (usageCount > 0) {
                throw new AppException(OrderErrorCode.PROMOTION_CANNOT_BE_MODIFIED);
            }
            if (promotionRepository.existsByCodeIgnoreCase(newCode)) {
                throw new AppException(OrderErrorCode.PROMOTION_CODE_EXISTS);
            }
            promotion.setCode(newCode);
        }

        promotion.setType(request.getType());
        promotion.setValue(request.getValue());
        promotion.setMaxDiscount(request.getMaxDiscount());
        promotion.setMinOrderValue(request.getMinOrderValue());
        promotion.setStartAt(request.getStartAt());
        promotion.setEndAt(request.getEndAt());
        promotion.setTotalQuota(request.getTotalQuota());
        promotion.setPerUserQuota(request.getPerUserQuota());
        promotion.setScopeType(request.getScopeType());
        promotion.setScopeIds(request.getScopeIds());
        if (request.getActive() != null) {
            promotion.setActive(request.getActive());
        }

        return toResponse(promotionRepository.save(promotion));
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getPromotionById(String id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new AppException(OrderErrorCode.PROMOTION_NOT_FOUND));
        return toResponse(promotion);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getPromotionByCode(String code) {
        Promotion promotion = promotionRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new AppException(OrderErrorCode.PROMOTION_NOT_FOUND));
        return toResponse(promotion);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<PromotionResponse>> getPromotions(Boolean active, Pageable pageable) {
        Page<Promotion> page = active != null
                ? promotionRepository.findByActive(active, pageable)
                : promotionRepository.findAll(pageable);

        List<PromotionResponse> items = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.<List<PromotionResponse>>builder()
                .pageNo(page.getNumber())
                .pageSize(page.getSize())
                .totalPage(page.getTotalPages())
                .items(items)
                .build();
    }

    @Override
    @Transactional
    public void togglePromotionActive(String id, boolean active) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new AppException(OrderErrorCode.PROMOTION_NOT_FOUND));
        promotion.setActive(active);
        promotionRepository.save(promotion);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionPreviewResponse validatePromotionForCustomer(String code, BigDecimal subtotal) {
        if (code == null || code.isBlank()) {
            return PromotionPreviewResponse.builder()
                    .code("")
                    .valid(false)
                    .discountAmount(BigDecimal.ZERO)
                    .message("Promotion code is required")
                    .build();
        }
        try {
            Promotion promotion = findAndValidatePromotion(code.trim(), null, subtotal, List.of());
            BigDecimal discount = calculateDiscount(promotion, subtotal, List.of());
            return PromotionPreviewResponse.builder()
                    .code(promotion.getCode())
                    .valid(true)
                    .discountAmount(discount)
                    .message("Promotion is valid")
                    .build();
        } catch (AppException e) {
            return PromotionPreviewResponse.builder()
                    .code(code.trim().toUpperCase())
                    .valid(false)
                    .discountAmount(BigDecimal.ZERO)
                    .message(e.getErrorCode().getMessage())
                    .build();
        }
    }

    private Promotion findAndValidatePromotion(String code, String userId, BigDecimal subtotal, List<PromotionItemDto> items) {
        Promotion promotion = promotionRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new AppException(OrderErrorCode.PROMOTION_NOT_FOUND));

        validatePromotionEligibility(promotion, userId, subtotal, items);
        return promotion;
    }

    private void validatePromotionEligibility(Promotion promotion, String userId, BigDecimal subtotal, List<PromotionItemDto> items) {
        if (!Boolean.TRUE.equals(promotion.getActive())) {
            throw new AppException(OrderErrorCode.PROMOTION_EXPIRED);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(promotion.getStartAt()) || now.isAfter(promotion.getEndAt())) {
            throw new AppException(OrderErrorCode.PROMOTION_EXPIRED);
        }

        if (promotion.getMinOrderValue() != null && subtotal != null && subtotal.compareTo(promotion.getMinOrderValue()) < 0) {
            throw new AppException(OrderErrorCode.PROMOTION_MIN_ORDER_NOT_MET);
        }

        List<CouponUsageStatus> activeStatuses = List.of(CouponUsageStatus.RESERVED, CouponUsageStatus.CONFIRMED);

        if (promotion.getTotalQuota() != null) {
            long usedCount = couponUsageRepository.countByPromotionIdAndStatusIn(promotion.getId(), activeStatuses);
            if (usedCount >= promotion.getTotalQuota()) {
                throw new AppException(OrderErrorCode.PROMOTION_QUOTA_EXCEEDED);
            }
        }

        if (promotion.getPerUserQuota() != null && userId != null && !userId.isBlank()) {
            long userUsedCount = couponUsageRepository.countByPromotionIdAndUserIdAndStatusIn(promotion.getId(), userId, activeStatuses);
            if (userUsedCount >= promotion.getPerUserQuota()) {
                throw new AppException(OrderErrorCode.PROMOTION_QUOTA_EXCEEDED);
            }
        }

        if (promotion.getScopeType() != PromotionScopeType.ALL && items != null && !items.isEmpty()) {
            getApplicableAmount(promotion, subtotal, items);
        }
    }

    private void validatePromotionRequest(PromotionRequest request) {
        if (request.getStartAt() == null || request.getEndAt() == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }
        if (!request.getEndAt().isAfter(request.getStartAt())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }
        if (request.getValue() == null || request.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }
        if (request.getType() == PromotionType.PERCENT) {
            if (request.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED);
            }
        } else if (request.getType() == PromotionType.FIXED) {
            if (request.getMaxDiscount() != null) {
                throw new AppException(ErrorCode.VALIDATION_FAILED);
            }
        }
        if (request.getScopeType() != PromotionScopeType.ALL) {
            if (request.getScopeIds() == null || request.getScopeIds().trim().isEmpty()) {
                throw new AppException(ErrorCode.VALIDATION_FAILED);
            }
        }
    }

    private BigDecimal calculateDiscount(Promotion promotion, BigDecimal subtotal, List<PromotionItemDto> items) {
        BigDecimal applicableAmount = getApplicableAmount(promotion, subtotal, items);

        BigDecimal discount;
        if (promotion.getType() == PromotionType.PERCENT) {
            discount = applicableAmount.multiply(promotion.getValue())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            if (promotion.getMaxDiscount() != null) {
                discount = discount.min(promotion.getMaxDiscount());
            }
        } else {
            discount = promotion.getValue();
        }

        return discount.min(applicableAmount).max(BigDecimal.ZERO);
    }

    private BigDecimal getApplicableAmount(Promotion promotion, BigDecimal subtotal, List<PromotionItemDto> items) {
        if (promotion.getScopeType() == PromotionScopeType.ALL) {
            return subtotal != null ? subtotal : BigDecimal.ZERO;
        }

        if (promotion.getScopeIds() == null || promotion.getScopeIds().isBlank()) {
            return subtotal != null ? subtotal : BigDecimal.ZERO;
        }

        Set<String> scopeIdSet = Arrays.stream(promotion.getScopeIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        if (items == null || items.isEmpty()) {
            throw new AppException(OrderErrorCode.PROMOTION_SCOPE_MISMATCH);
        }

        List<PromotionItemDto> matchingItems;
        if (promotion.getScopeType() == PromotionScopeType.PRODUCT) {
            matchingItems = items.stream()
                    .filter(item -> (item.getProductId() != null && scopeIdSet.contains(item.getProductId()))
                            || (item.getVariantId() != null && scopeIdSet.contains(item.getVariantId())))
                    .toList();
        } else if (promotion.getScopeType() == PromotionScopeType.CATEGORY) {
            matchingItems = items.stream()
                    .filter(item -> item.getCategoryId() != null && scopeIdSet.contains(item.getCategoryId()))
                    .toList();
        } else {
            matchingItems = items;
        }

        if (matchingItems.isEmpty()) {
            throw new AppException(OrderErrorCode.PROMOTION_SCOPE_MISMATCH);
        }

        return matchingItems.stream()
                .map(item -> item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PromotionResponse toResponse(Promotion promotion) {
        return PromotionResponse.builder()
                .id(promotion.getId())
                .code(promotion.getCode())
                .type(promotion.getType())
                .value(promotion.getValue())
                .maxDiscount(promotion.getMaxDiscount())
                .minOrderValue(promotion.getMinOrderValue())
                .startAt(promotion.getStartAt())
                .endAt(promotion.getEndAt())
                .totalQuota(promotion.getTotalQuota())
                .perUserQuota(promotion.getPerUserQuota())
                .scopeType(promotion.getScopeType())
                .scopeIds(promotion.getScopeIds())
                .active(promotion.getActive())
                .createdAt(promotion.getCreatedAt())
                .updatedAt(promotion.getUpdatedAt())
                .build();
    }
}
