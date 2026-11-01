package com.fashionstore.order.promotion.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.BaseErrorCode;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.order.promotion.dto.AvailablePromotionResponse;
import com.fashionstore.order.promotion.dto.PromotionItemDto;
import com.fashionstore.order.promotion.dto.PromotionPreviewResponse;
import com.fashionstore.order.promotion.dto.PromotionRequest;
import com.fashionstore.order.promotion.dto.PromotionResponse;
import com.fashionstore.order.promotion.entity.CouponUsage;
import com.fashionstore.order.promotion.entity.Promotion;
import com.fashionstore.order.promotion.entity.CouponUsageStatus;
import com.fashionstore.order.promotion.entity.PromotionScopeType;
import com.fashionstore.order.promotion.dto.PromotionTarget;
import com.fashionstore.order.promotion.entity.PromotionType;
import com.fashionstore.order.shared.exception.OrderErrorCode;
import com.fashionstore.order.promotion.repository.CouponUsageRepository;
import com.fashionstore.order.promotion.repository.PromotionRepository;
import com.fashionstore.order.promotion.service.PromotionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PromotionServiceImpl implements PromotionService {

    private static final List<CouponUsageStatus> ACTIVE_STATUSES =
            List.of(CouponUsageStatus.RESERVED, CouponUsageStatus.CONFIRMED);

    PromotionRepository promotionRepository;
    CouponUsageRepository couponUsageRepository;

    @Override
    @Transactional(readOnly = true)
    public BigDecimal previewDiscount(String code, String userId, BigDecimal subtotal, List<PromotionItemDto> items) {
        if (code == null || code.isBlank()) {
            return BigDecimal.ZERO;
        }
        Promotion promotion = findAndValidatePromotion(code.trim(), PromotionTarget.PRODUCT, userId, subtotal, items);
        return PromotionCalculator.productDiscount(promotion, subtotal, items);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal previewShippingDiscount(String code, String userId, BigDecimal subtotal, BigDecimal shippingFee) {
        if (code == null || code.isBlank()) {
            return BigDecimal.ZERO;
        }
        Promotion promotion = findAndValidatePromotion(code.trim(), PromotionTarget.SHIPPING, userId, subtotal, null);
        return PromotionCalculator.shippingDiscount(promotion, shippingFee);
    }

    @Override
    @Transactional
    public BigDecimal reserve(String code, String userId, String orderId, BigDecimal subtotal, List<PromotionItemDto> items) {
        return reserveUsage(code, PromotionTarget.PRODUCT, userId, orderId, subtotal, items, null);
    }

    @Override
    @Transactional
    public BigDecimal reserveShipping(
            String code, String userId, String orderId, BigDecimal subtotal, BigDecimal shippingFee) {
        return reserveUsage(code, PromotionTarget.SHIPPING, userId, orderId, subtotal, null, shippingFee);
    }

    private BigDecimal reserveUsage(String code, PromotionTarget target, String userId, String orderId,
                                    BigDecimal subtotal, List<PromotionItemDto> items, BigDecimal shippingFee) {
        if (code == null || code.isBlank()) {
            return BigDecimal.ZERO;
        }
        // Khóa bi quan trên promotion để ngăn race condition vượt quota
        Promotion promotion = promotionRepository.findForUpdateByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new AppException(OrderErrorCode.PROMOTION_NOT_FOUND));
        requireTarget(promotion, target);
        BigDecimal discount = PromotionCalculator.discountFor(promotion, subtotal, items, shippingFee);
        if (couponUsageRepository.existsByOrderIdAndPromotionId(orderId, promotion.getId())) {
            return discount; // Idempotent: already reserved
        }

        validatePromotionEligibility(promotion, userId, subtotal, items);

        couponUsageRepository.save(CouponUsage.builder()
                .promotion(promotion)
                .userId(userId)
                .orderId(orderId)
                .usedAt(LocalDateTime.now())
                .status(CouponUsageStatus.RESERVED)
                .build());
        log.info("Reserved coupon {} for user {} order {}", promotion.getCode(), userId, orderId);
        return discount;
    }

    @Override
    @Transactional
    public void confirm(String orderId) {
        for (CouponUsage usage : couponUsageRepository.findAllByOrderId(orderId)) {
            if (usage.getStatus() == CouponUsageStatus.RESERVED) {
                usage.setStatus(CouponUsageStatus.CONFIRMED);
                couponUsageRepository.save(usage);
                log.info("Confirmed coupon usage for order {}", orderId);
            }
        }
    }

    @Override
    @Transactional
    public void release(String orderId) {
        for (CouponUsage usage : couponUsageRepository.findAllByOrderId(orderId)) {
            if (usage.getStatus() != CouponUsageStatus.RELEASED) {
                usage.setStatus(CouponUsageStatus.RELEASED);
                couponUsageRepository.save(usage);
                log.info("Released coupon usage for order {}", orderId);
            }
        }
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
                .target(resolveTarget(request))
                .description(request.getDescription())
                .isPublic(Boolean.TRUE.equals(request.getIsPublic()))
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

        return toResponse(promotionRepository.save(promotion), Map.of());
    }

    @Override
    @Transactional
    public PromotionResponse updatePromotion(String id, PromotionRequest request) {
        validatePromotionRequest(request);

        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new AppException(OrderErrorCode.PROMOTION_NOT_FOUND));

        String newCode = request.getCode().trim().toUpperCase();
        boolean isCodeChanged = !promotion.getCode().equalsIgnoreCase(newCode);
        boolean isTargetChanged = resolveTarget(request) != promotion.getTarget();
        if ((isCodeChanged || isTargetChanged) && hasUsage(id)) {
            throw new AppException(OrderErrorCode.PROMOTION_CANNOT_BE_MODIFIED);
        }
        if (isCodeChanged) {
            if (promotionRepository.existsByCodeIgnoreCase(newCode)) {
                throw new AppException(OrderErrorCode.PROMOTION_CODE_EXISTS);
            }
            promotion.setCode(newCode);
        }

        promotion.setTarget(resolveTarget(request));
        promotion.setDescription(request.getDescription());
        promotion.setIsPublic(Boolean.TRUE.equals(request.getIsPublic()));
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

        return toResponse(promotionRepository.save(promotion), usageCounts(List.of(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getPromotionById(String id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new AppException(OrderErrorCode.PROMOTION_NOT_FOUND));
        return toResponse(promotion, usageCounts(List.of(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getPromotionByCode(String code) {
        Promotion promotion = promotionRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new AppException(OrderErrorCode.PROMOTION_NOT_FOUND));
        return toResponse(promotion, usageCounts(List.of(promotion.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<PromotionResponse>> getPromotions(
            Boolean active, PromotionTarget target, Pageable pageable) {
        Page<Promotion> page = promotionRepository.search(active, target, pageable);

        Map<String, long[]> counts = usageCounts(page.getContent().stream().map(Promotion::getId).toList());
        List<PromotionResponse> items = page.getContent().stream()
                .map(promotion -> toResponse(promotion, counts))
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
    public PromotionPreviewResponse validatePromotionForCustomer(
            String code, String userId, BigDecimal subtotal, List<PromotionItemDto> items) {
        if (code == null || code.isBlank()) {
            return PromotionPreviewResponse.builder()
                    .code("")
                    .valid(false)
                    .discountAmount(BigDecimal.ZERO)
                    .message("Promotion code is required")
                    .build();
        }
        try {
            Promotion promotion = findAndValidatePromotion(code.trim(), PromotionTarget.PRODUCT, userId, subtotal, items);
            BigDecimal discount = PromotionCalculator.productDiscount(promotion, subtotal, items);
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

    @Override
    @Transactional(readOnly = true)
    public List<AvailablePromotionResponse> listAvailable(
            String userId, PromotionTarget target, BigDecimal subtotal, List<PromotionItemDto> items) {
        return promotionRepository.findPublicCurrentlyValid(LocalDateTime.now(), target).stream()
                .map(promotion -> toAvailable(promotion, userId, subtotal, items))
                .filter(Objects::nonNull)
                .toList();
    }

    /** Null means the coupon cannot be shown at all (quota used up); cart-specific misses stay listed as ineligible. */
    private AvailablePromotionResponse toAvailable(
            Promotion promotion, String userId, BigDecimal subtotal, List<PromotionItemDto> items) {
        try {
            validatePromotionEligibility(promotion, userId, subtotal, items);
            BigDecimal estimated = promotion.getTarget() == PromotionTarget.PRODUCT
                    ? PromotionCalculator.productDiscount(promotion, subtotal, items) : null;
            return toAvailable(promotion, true, null, estimated);
        } catch (AppException e) {
            boolean isCartSpecific = e.getErrorCode() == OrderErrorCode.PROMOTION_MIN_ORDER_NOT_MET
                    || e.getErrorCode() == OrderErrorCode.PROMOTION_SCOPE_MISMATCH;
            return isCartSpecific ? toAvailable(promotion, false, e.getErrorCode(), null) : null;
        }
    }

    private AvailablePromotionResponse toAvailable(
            Promotion promotion, boolean isEligible, BaseErrorCode reason, BigDecimal estimatedDiscount) {
        return new AvailablePromotionResponse(
                promotion.getCode(), promotion.getDescription(), promotion.getTarget(), promotion.getType(),
                promotion.getValue(), promotion.getMaxDiscount(), promotion.getMinOrderValue(), promotion.getEndAt(),
                promotion.getScopeType(), isEligible,
                reason == null ? null : reason.getCode(), reason == null ? null : reason.getMessage(),
                estimatedDiscount);
    }

    private Promotion findAndValidatePromotion(
            String code, PromotionTarget target, String userId, BigDecimal subtotal, List<PromotionItemDto> items) {
        Promotion promotion = promotionRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new AppException(OrderErrorCode.PROMOTION_NOT_FOUND));
        requireTarget(promotion, target);
        validatePromotionEligibility(promotion, userId, subtotal, items);
        return promotion;
    }

    private void requireTarget(Promotion promotion, PromotionTarget target) {
        if (promotion.getTarget() != target) {
            throw new AppException(OrderErrorCode.PROMOTION_TARGET_MISMATCH);
        }
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

        if (promotion.getTotalQuota() != null) {
            long usedCount = couponUsageRepository.countByPromotionIdAndStatusIn(promotion.getId(), ACTIVE_STATUSES);
            if (usedCount >= promotion.getTotalQuota()) {
                throw new AppException(OrderErrorCode.PROMOTION_QUOTA_EXCEEDED);
            }
        }

        if (promotion.getPerUserQuota() != null && userId != null && !userId.isBlank()) {
            long userUsedCount = couponUsageRepository.countByPromotionIdAndUserIdAndStatusIn(
                    promotion.getId(), userId, ACTIVE_STATUSES);
            if (userUsedCount >= promotion.getPerUserQuota()) {
                throw new AppException(OrderErrorCode.PROMOTION_QUOTA_EXCEEDED);
            }
        }

        if (promotion.getScopeType() != PromotionScopeType.ALL && items != null && !items.isEmpty()) {
            PromotionCalculator.productDiscount(promotion, subtotal, items);
        }
    }

    private boolean hasUsage(String promotionId) {
        return couponUsageRepository.countByPromotionIdAndStatusIn(promotionId, ACTIVE_STATUSES) > 0;
    }

    private PromotionTarget resolveTarget(PromotionRequest request) {
        return request.getTarget() == null ? PromotionTarget.PRODUCT : request.getTarget();
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
        if (resolveTarget(request) == PromotionTarget.SHIPPING && request.getScopeType() != PromotionScopeType.ALL) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /** One group-by query for a whole page of promotions: id -> [reserved, confirmed]. */
    private Map<String, long[]> usageCounts(Collection<String> promotionIds) {
        List<String> ids = promotionIds.stream().filter(Objects::nonNull).toList();
        Map<String, long[]> counts = new HashMap<>();
        if (ids.isEmpty()) {
            return counts;
        }
        for (Object[] row : couponUsageRepository.countByPromotionIdsGroupedByStatus(ids)) {
            long[] pair = counts.computeIfAbsent((String) row[0], key -> new long[2]);
            if (row[1] == CouponUsageStatus.RESERVED) {
                pair[0] = (Long) row[2];
            } else if (row[1] == CouponUsageStatus.CONFIRMED) {
                pair[1] = (Long) row[2];
            }
        }
        return counts;
    }

    private PromotionResponse toResponse(Promotion promotion, Map<String, long[]> usageCounts) {
        long[] counts = usageCounts.getOrDefault(promotion.getId(), new long[2]);
        return PromotionResponse.builder()
                .id(promotion.getId())
                .code(promotion.getCode())
                .target(promotion.getTarget())
                .description(promotion.getDescription())
                .isPublic(promotion.getIsPublic())
                .reservedCount(counts[0])
                .confirmedCount(counts[1])
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
