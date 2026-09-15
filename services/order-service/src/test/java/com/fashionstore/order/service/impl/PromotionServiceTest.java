package com.fashionstore.order.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.order.dto.PromotionItemDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private CouponUsageRepository couponUsageRepository;

    private PromotionServiceImpl promotionService;

    @BeforeEach
    void setUp() {
        promotionService = new PromotionServiceImpl(promotionRepository, couponUsageRepository);
    }

    @Test
    void previewDiscount_blankCode_returnsZero() {
        BigDecimal discount = promotionService.previewDiscount("", "user-1", new BigDecimal("500000"), List.of());
        assertEquals(0, discount.compareTo(BigDecimal.ZERO));

        discount = promotionService.previewDiscount(null, "user-1", new BigDecimal("500000"), List.of());
        assertEquals(0, discount.compareTo(BigDecimal.ZERO));
    }

    @Test
    void previewDiscount_notFound_throwsException() {
        when(promotionRepository.findByCodeIgnoreCase("INVALID")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                promotionService.previewDiscount("INVALID", "user-1", new BigDecimal("500000"), List.of()));
        assertEquals(OrderErrorCode.PROMOTION_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void previewDiscount_expired_throwsException() {
        Promotion expiredPromo = Promotion.builder()
                .code("EXPIRED")
                .type(PromotionType.PERCENT)
                .value(new BigDecimal("10"))
                .startAt(LocalDateTime.now().minusDays(10))
                .endAt(LocalDateTime.now().minusDays(1))
                .scopeType(PromotionScopeType.ALL)
                .active(true)
                .build();
        when(promotionRepository.findByCodeIgnoreCase("EXPIRED")).thenReturn(Optional.of(expiredPromo));

        AppException ex = assertThrows(AppException.class, () ->
                promotionService.previewDiscount("EXPIRED", "user-1", new BigDecimal("500000"), List.of()));
        assertEquals(OrderErrorCode.PROMOTION_EXPIRED, ex.getErrorCode());
    }

    @Test
    void previewDiscount_inactive_throwsException() {
        Promotion inactivePromo = Promotion.builder()
                .code("INACTIVE")
                .type(PromotionType.PERCENT)
                .value(new BigDecimal("10"))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .scopeType(PromotionScopeType.ALL)
                .active(false)
                .build();
        when(promotionRepository.findByCodeIgnoreCase("INACTIVE")).thenReturn(Optional.of(inactivePromo));

        AppException ex = assertThrows(AppException.class, () ->
                promotionService.previewDiscount("INACTIVE", "user-1", new BigDecimal("500000"), List.of()));
        assertEquals(OrderErrorCode.PROMOTION_EXPIRED, ex.getErrorCode());
    }

    @Test
    void previewDiscount_totalQuotaExceeded_throwsException() {
        Promotion promo = Promotion.builder()
                .code("QUOTAFULL")
                .type(PromotionType.PERCENT)
                .value(new BigDecimal("10"))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .totalQuota(5)
                .scopeType(PromotionScopeType.ALL)
                .active(true)
                .build();
        promo.setId("promo-1");
        when(promotionRepository.findByCodeIgnoreCase("QUOTAFULL")).thenReturn(Optional.of(promo));
        when(couponUsageRepository.countByPromotionIdAndStatusIn(eq("promo-1"), any())).thenReturn(5L);

        AppException ex = assertThrows(AppException.class, () ->
                promotionService.previewDiscount("QUOTAFULL", "user-1", new BigDecimal("500000"), List.of()));
        assertEquals(OrderErrorCode.PROMOTION_QUOTA_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void previewDiscount_perUserQuotaExceeded_throwsException() {
        Promotion promo = Promotion.builder()
                .code("USERQUOTA")
                .type(PromotionType.PERCENT)
                .value(new BigDecimal("10"))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .perUserQuota(1)
                .scopeType(PromotionScopeType.ALL)
                .active(true)
                .build();
        promo.setId("promo-1");
        when(promotionRepository.findByCodeIgnoreCase("USERQUOTA")).thenReturn(Optional.of(promo));
        when(couponUsageRepository.countByPromotionIdAndUserIdAndStatusIn(eq("promo-1"), eq("user-1"), any())).thenReturn(1L);

        AppException ex = assertThrows(AppException.class, () ->
                promotionService.previewDiscount("USERQUOTA", "user-1", new BigDecimal("500000"), List.of()));
        assertEquals(OrderErrorCode.PROMOTION_QUOTA_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void previewDiscount_minOrderValueNotMet_throwsException() {
        Promotion promo = Promotion.builder()
                .code("MINORDER")
                .type(PromotionType.FIXED)
                .value(new BigDecimal("50000"))
                .minOrderValue(new BigDecimal("300000"))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .scopeType(PromotionScopeType.ALL)
                .active(true)
                .build();
        when(promotionRepository.findByCodeIgnoreCase("MINORDER")).thenReturn(Optional.of(promo));

        AppException ex = assertThrows(AppException.class, () ->
                promotionService.previewDiscount("MINORDER", "user-1", new BigDecimal("200000"), List.of()));
        assertEquals(OrderErrorCode.PROMOTION_MIN_ORDER_NOT_MET, ex.getErrorCode());
    }

    @Test
    void previewDiscount_percentWithCap_calculatesCorrectly() {
        Promotion promo = Promotion.builder()
                .code("SALE10")
                .type(PromotionType.PERCENT)
                .value(new BigDecimal("10")) // 10%
                .maxDiscount(new BigDecimal("30000")) // max 30k
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .scopeType(PromotionScopeType.ALL)
                .active(true)
                .build();
        when(promotionRepository.findByCodeIgnoreCase("SALE10")).thenReturn(Optional.of(promo));

        // Subtotal = 500,000 -> 10% is 50,000 -> capped at 30,000
        BigDecimal discount = promotionService.previewDiscount("SALE10", "user-1", new BigDecimal("500000"), List.of());
        assertEquals(0, discount.compareTo(new BigDecimal("30000")));

        // Subtotal = 200,000 -> 10% is 20,000 -> below cap -> 20,000
        discount = promotionService.previewDiscount("SALE10", "user-1", new BigDecimal("200000"), List.of());
        assertEquals(0, discount.compareTo(new BigDecimal("20000")));
    }

    @Test
    void previewDiscount_fixedAmount_calculatesCorrectly() {
        Promotion promo = Promotion.builder()
                .code("FIXED50K")
                .type(PromotionType.FIXED)
                .value(new BigDecimal("50000"))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .scopeType(PromotionScopeType.ALL)
                .active(true)
                .build();
        when(promotionRepository.findByCodeIgnoreCase("FIXED50K")).thenReturn(Optional.of(promo));

        BigDecimal discount = promotionService.previewDiscount("FIXED50K", "user-1", new BigDecimal("200000"), List.of());
        assertEquals(0, discount.compareTo(new BigDecimal("50000")));
    }

    @Test
    void previewDiscount_productScope_appliesOnlyToMatchingProducts() {
        Promotion promo = Promotion.builder()
                .code("PROD10")
                .type(PromotionType.PERCENT)
                .value(new BigDecimal("10"))
                .scopeType(PromotionScopeType.PRODUCT)
                .scopeIds("prod-1, prod-2")
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .active(true)
                .build();
        when(promotionRepository.findByCodeIgnoreCase("PROD10")).thenReturn(Optional.of(promo));

        // Items: prod-1 (100k) + prod-3 (200k) = subtotal 300k
        // Discount should be 10% of 100k = 10,000
        List<PromotionItemDto> items = List.of(
                PromotionItemDto.builder().productId("prod-1").lineTotal(new BigDecimal("100000")).build(),
                PromotionItemDto.builder().productId("prod-3").lineTotal(new BigDecimal("200000")).build()
        );

        BigDecimal discount = promotionService.previewDiscount("PROD10", "user-1", new BigDecimal("300000"), items);
        assertEquals(0, discount.compareTo(new BigDecimal("10000")));
    }

    @Test
    void previewDiscount_productScope_noMatchingProducts_throwsMismatch() {
        Promotion promo = Promotion.builder()
                .code("PROD10")
                .type(PromotionType.PERCENT)
                .value(new BigDecimal("10"))
                .scopeType(PromotionScopeType.PRODUCT)
                .scopeIds("prod-1, prod-2")
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .active(true)
                .build();
        when(promotionRepository.findByCodeIgnoreCase("PROD10")).thenReturn(Optional.of(promo));

        List<PromotionItemDto> items = List.of(
                PromotionItemDto.builder().productId("prod-99").lineTotal(new BigDecimal("200000")).build()
        );

        AppException ex = assertThrows(AppException.class, () ->
                promotionService.previewDiscount("PROD10", "user-1", new BigDecimal("200000"), items));
        assertEquals(OrderErrorCode.PROMOTION_SCOPE_MISMATCH, ex.getErrorCode());
    }

    @Test
    void reserve_savesCouponUsageAsReserved() {
        Promotion promo = Promotion.builder()
                .code("SALE10")
                .type(PromotionType.FIXED)
                .value(new BigDecimal("50000"))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .scopeType(PromotionScopeType.ALL)
                .active(true)
                .build();
        promo.setId("promo-1");
        when(promotionRepository.findForUpdateByCodeIgnoreCase("SALE10")).thenReturn(Optional.of(promo));
        when(couponUsageRepository.findByOrderId("order-1")).thenReturn(Optional.empty());

        promotionService.reserve("SALE10", "user-1", "order-1", new BigDecimal("200000"), List.of());

        ArgumentCaptor<CouponUsage> captor = ArgumentCaptor.forClass(CouponUsage.class);
        verify(couponUsageRepository).save(captor.capture());
        CouponUsage saved = captor.getValue();
        assertEquals("promo-1", saved.getPromotion().getId());
        assertEquals("user-1", saved.getUserId());
        assertEquals("order-1", saved.getOrderId());
        assertEquals(CouponUsageStatus.RESERVED, saved.getStatus());
    }

    @Test
    void confirm_updatesStatusToConfirmed() {
        CouponUsage usage = CouponUsage.builder()
                .orderId("order-1")
                .status(CouponUsageStatus.RESERVED)
                .build();
        when(couponUsageRepository.findByOrderId("order-1")).thenReturn(Optional.of(usage));

        promotionService.confirm("order-1");

        assertEquals(CouponUsageStatus.CONFIRMED, usage.getStatus());
        verify(couponUsageRepository).save(usage);
    }

    @Test
    void release_updatesStatusToReleased() {
        CouponUsage usage = CouponUsage.builder()
                .orderId("order-1")
                .status(CouponUsageStatus.RESERVED)
                .build();
        when(couponUsageRepository.findByOrderId("order-1")).thenReturn(Optional.of(usage));

        promotionService.release("order-1");

        assertEquals(CouponUsageStatus.RELEASED, usage.getStatus());
        verify(couponUsageRepository).save(usage);
    }

    @Test
    void createPromotion_duplicateCode_throwsPromotionCodeExists() {
        PromotionRequest request = PromotionRequest.builder()
                .code("DUPLICATE")
                .type(PromotionType.FIXED)
                .value(new BigDecimal("10000"))
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(5))
                .scopeType(PromotionScopeType.ALL)
                .build();
        when(promotionRepository.existsByCodeIgnoreCase("DUPLICATE")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> promotionService.createPromotion(request));
        assertEquals(OrderErrorCode.PROMOTION_CODE_EXISTS, ex.getErrorCode());
    }

    @Test
    void createPromotion_endAtBeforeStartAt_throwsValidationFailed() {
        PromotionRequest request = PromotionRequest.builder()
                .code("INVALID_DATES")
                .type(PromotionType.FIXED)
                .value(new BigDecimal("10000"))
                .startAt(LocalDateTime.now().plusDays(5))
                .endAt(LocalDateTime.now())
                .scopeType(PromotionScopeType.ALL)
                .build();

        AppException ex = assertThrows(AppException.class, () -> promotionService.createPromotion(request));
        assertEquals(com.fashionstore.common.exception.ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    void createPromotion_scopeNotAllWithoutScopeIds_throwsValidationFailed() {
        PromotionRequest request = PromotionRequest.builder()
                .code("INVALID_SCOPE")
                .type(PromotionType.FIXED)
                .value(new BigDecimal("10000"))
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(5))
                .scopeType(PromotionScopeType.PRODUCT)
                .scopeIds("")
                .build();

        AppException ex = assertThrows(AppException.class, () -> promotionService.createPromotion(request));
        assertEquals(com.fashionstore.common.exception.ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    void updatePromotion_changeCodeWhenUsed_throwsCannotBeModified() {
        Promotion promo = Promotion.builder()
                .code("OLDCODE")
                .type(PromotionType.FIXED)
                .value(new BigDecimal("10000"))
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(5))
                .scopeType(PromotionScopeType.ALL)
                .build();
        promo.setId("promo-1");
        when(promotionRepository.findById("promo-1")).thenReturn(Optional.of(promo));
        when(couponUsageRepository.countByPromotionIdAndStatusIn(eq("promo-1"), any())).thenReturn(2L);

        PromotionRequest request = PromotionRequest.builder()
                .code("NEWCODE")
                .type(PromotionType.FIXED)
                .value(new BigDecimal("10000"))
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(5))
                .scopeType(PromotionScopeType.ALL)
                .build();

        AppException ex = assertThrows(AppException.class, () -> promotionService.updatePromotion("promo-1", request));
        assertEquals(OrderErrorCode.PROMOTION_CANNOT_BE_MODIFIED, ex.getErrorCode());
    }

    @Test
    void validatePromotionForCustomer_validCode_returnsValidResponse() {
        Promotion promo = Promotion.builder()
                .code("VALID10")
                .type(PromotionType.PERCENT)
                .value(new BigDecimal("10"))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .scopeType(PromotionScopeType.ALL)
                .active(true)
                .build();
        when(promotionRepository.findByCodeIgnoreCase("VALID10")).thenReturn(Optional.of(promo));

        var response = promotionService.validatePromotionForCustomer("VALID10", new BigDecimal("100000"));
        assertTrue(response.isValid());
        assertEquals("VALID10", response.getCode());
        assertEquals(0, response.getDiscountAmount().compareTo(new BigDecimal("10000")));
    }

    @Test
    void validatePromotionForCustomer_invalidCode_returnsInvalidResponse() {
        when(promotionRepository.findByCodeIgnoreCase("INVALID")).thenReturn(Optional.empty());

        var response = promotionService.validatePromotionForCustomer("INVALID", new BigDecimal("100000"));
        assertFalse(response.isValid());
        assertEquals(0, response.getDiscountAmount().compareTo(BigDecimal.ZERO));
    }
}
