package com.fashionstore.order.promotion.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.order.promotion.dto.AvailablePromotionResponse;
import com.fashionstore.order.promotion.dto.PromotionItemDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
    void previewDiscount_categoryScope_matchesWhenAnyCategoryOfItemIsInScope() {
        Promotion promo = Promotion.builder()
                .code("CAT10")
                .type(PromotionType.PERCENT)
                .value(new BigDecimal("10"))
                .scopeType(PromotionScopeType.CATEGORY)
                .scopeIds("cat-2")
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .active(true)
                .build();
        when(promotionRepository.findByCodeIgnoreCase("CAT10")).thenReturn(Optional.of(promo));

        List<PromotionItemDto> items = List.of(
                PromotionItemDto.builder().categoryIds(List.of("cat-1", "cat-2")).lineTotal(new BigDecimal("100000")).build(),
                PromotionItemDto.builder().categoryIds(List.of("cat-3")).lineTotal(new BigDecimal("200000")).build()
        );

        BigDecimal discount = promotionService.previewDiscount("CAT10", "user-1", new BigDecimal("300000"), items);
        assertEquals(0, discount.compareTo(new BigDecimal("10000")));
    }

    @Test
    void previewDiscount_categoryScope_itemWithoutCategories_throwsMismatch() {
        Promotion promo = Promotion.builder()
                .code("CAT10")
                .type(PromotionType.PERCENT)
                .value(new BigDecimal("10"))
                .scopeType(PromotionScopeType.CATEGORY)
                .scopeIds("cat-2")
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .active(true)
                .build();
        when(promotionRepository.findByCodeIgnoreCase("CAT10")).thenReturn(Optional.of(promo));

        List<PromotionItemDto> items = List.of(
                PromotionItemDto.builder().lineTotal(new BigDecimal("200000")).build()
        );

        AppException ex = assertThrows(AppException.class, () ->
                promotionService.previewDiscount("CAT10", "user-1", new BigDecimal("200000"), items));
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

        BigDecimal discount = promotionService.reserve("SALE10", "user-1", "order-1", new BigDecimal("200000"), List.of());

        assertEquals(0, discount.compareTo(new BigDecimal("50000")));

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
        when(couponUsageRepository.findAllByOrderId("order-1")).thenReturn(List.of(usage));

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
        when(couponUsageRepository.findAllByOrderId("order-1")).thenReturn(List.of(usage));

        promotionService.release("order-1");

        assertEquals(CouponUsageStatus.RELEASED, usage.getStatus());
        verify(couponUsageRepository).save(usage);
    }

    private Promotion shippingPromo(String code, PromotionType type, String value) {
        Promotion promo = Promotion.builder()
                .code(code)
                .target(PromotionTarget.SHIPPING)
                .type(type)
                .value(new BigDecimal(value))
                .scopeType(PromotionScopeType.ALL)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .active(true)
                .build();
        promo.setId("promo-" + code);
        return promo;
    }

    private Promotion publicPromo(String id, String code, PromotionType type, String value) {
        Promotion promo = Promotion.builder()
                .code(code)
                .type(type)
                .value(new BigDecimal(value))
                .scopeType(PromotionScopeType.ALL)
                .isPublic(true)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .build();
        promo.setId(id);
        return promo;
    }

    @Test
    void previewShippingDiscount_fixedLargerThanFee_isCappedAtTheFee() {
        when(promotionRepository.findByCodeIgnoreCase("SHIP50"))
                .thenReturn(Optional.of(shippingPromo("SHIP50", PromotionType.FIXED, "50000")));

        BigDecimal discount = promotionService.previewShippingDiscount(
                "SHIP50", "user-1", new BigDecimal("300000"), new BigDecimal("25000"));

        assertEquals(0, discount.compareTo(new BigDecimal("25000")));
    }

    @Test
    void previewShippingDiscount_percent100_makesShippingFree() {
        when(promotionRepository.findByCodeIgnoreCase("FREESHIP"))
                .thenReturn(Optional.of(shippingPromo("FREESHIP", PromotionType.PERCENT, "100")));

        BigDecimal discount = promotionService.previewShippingDiscount(
                "FREESHIP", "user-1", new BigDecimal("300000"), new BigDecimal("40000"));

        assertEquals(0, discount.compareTo(new BigDecimal("40000")));
    }

    @Test
    void previewShippingDiscount_productCouponInShippingSlot_throwsTargetMismatch() {
        when(promotionRepository.findByCodeIgnoreCase("SALE10"))
                .thenReturn(Optional.of(publicPromo("p-1", "SALE10", PromotionType.PERCENT, "10")));

        AppException ex = assertThrows(AppException.class, () -> promotionService.previewShippingDiscount(
                "SALE10", "user-1", new BigDecimal("300000"), new BigDecimal("25000")));

        assertEquals(OrderErrorCode.PROMOTION_TARGET_MISMATCH, ex.getErrorCode());
    }

    @Test
    void previewDiscount_shippingCouponInProductSlot_throwsTargetMismatch() {
        when(promotionRepository.findByCodeIgnoreCase("FREESHIP"))
                .thenReturn(Optional.of(shippingPromo("FREESHIP", PromotionType.PERCENT, "100")));

        AppException ex = assertThrows(AppException.class, () ->
                promotionService.previewDiscount("FREESHIP", "user-1", new BigDecimal("300000"), List.of()));

        assertEquals(OrderErrorCode.PROMOTION_TARGET_MISMATCH, ex.getErrorCode());
    }

    @Test
    void reserveShipping_savesSecondUsageForAnOrderThatAlreadyHasAProductCoupon() {
        Promotion promo = shippingPromo("SHIP20K", PromotionType.FIXED, "20000");
        when(promotionRepository.findForUpdateByCodeIgnoreCase("SHIP20K")).thenReturn(Optional.of(promo));
        when(couponUsageRepository.existsByOrderIdAndPromotionId("order-1", "promo-SHIP20K")).thenReturn(false);

        BigDecimal discount = promotionService.reserveShipping(
                "SHIP20K", "user-1", "order-1", new BigDecimal("300000"), new BigDecimal("25000"));

        assertEquals(0, discount.compareTo(new BigDecimal("20000")));
        ArgumentCaptor<CouponUsage> captor = ArgumentCaptor.forClass(CouponUsage.class);
        verify(couponUsageRepository).save(captor.capture());
        assertEquals("promo-SHIP20K", captor.getValue().getPromotion().getId());
        assertEquals(CouponUsageStatus.RESERVED, captor.getValue().getStatus());
    }

    @Test
    void reserveShipping_alreadyReservedForTheOrder_isIdempotentAndSavesNothing() {
        Promotion promo = shippingPromo("SHIP20K", PromotionType.FIXED, "20000");
        when(promotionRepository.findForUpdateByCodeIgnoreCase("SHIP20K")).thenReturn(Optional.of(promo));
        when(couponUsageRepository.existsByOrderIdAndPromotionId("order-1", "promo-SHIP20K")).thenReturn(true);

        BigDecimal discount = promotionService.reserveShipping(
                "SHIP20K", "user-1", "order-1", new BigDecimal("300000"), new BigDecimal("25000"));

        assertEquals(0, discount.compareTo(new BigDecimal("20000")));
        verify(couponUsageRepository, never()).save(any());
    }

    @Test
    void confirmAndRelease_applyToEveryCouponUsageOfTheOrder() {
        CouponUsage productUsage = CouponUsage.builder().orderId("order-1").status(CouponUsageStatus.RESERVED).build();
        CouponUsage shippingUsage = CouponUsage.builder().orderId("order-1").status(CouponUsageStatus.RESERVED).build();
        when(couponUsageRepository.findAllByOrderId("order-1")).thenReturn(List.of(productUsage, shippingUsage));

        promotionService.confirm("order-1");

        assertEquals(CouponUsageStatus.CONFIRMED, productUsage.getStatus());
        assertEquals(CouponUsageStatus.CONFIRMED, shippingUsage.getStatus());

        promotionService.release("order-1");

        assertEquals(CouponUsageStatus.RELEASED, productUsage.getStatus());
        assertEquals(CouponUsageStatus.RELEASED, shippingUsage.getStatus());
    }

    @Test
    void createPromotion_shippingCouponWithProductScope_isRejected() {
        PromotionRequest request = PromotionRequest.builder()
                .code("SHIPSCOPE")
                .target(PromotionTarget.SHIPPING)
                .type(PromotionType.FIXED)
                .value(new BigDecimal("20000"))
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(5))
                .scopeType(PromotionScopeType.PRODUCT)
                .scopeIds("prod-1")
                .build();

        AppException ex = assertThrows(AppException.class, () -> promotionService.createPromotion(request));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    void updatePromotion_changingTargetAfterUsage_isRejected() {
        Promotion promo = shippingPromo("SHIP20K", PromotionType.FIXED, "20000");
        when(promotionRepository.findById("promo-SHIP20K")).thenReturn(Optional.of(promo));
        when(couponUsageRepository.countByPromotionIdAndStatusIn(eq("promo-SHIP20K"), any())).thenReturn(1L);
        PromotionRequest request = PromotionRequest.builder()
                .code("SHIP20K")
                .target(PromotionTarget.PRODUCT)
                .type(PromotionType.FIXED)
                .value(new BigDecimal("20000"))
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(5))
                .scopeType(PromotionScopeType.ALL)
                .build();

        AppException ex = assertThrows(AppException.class,
                () -> promotionService.updatePromotion("promo-SHIP20K", request));

        assertEquals(OrderErrorCode.PROMOTION_CANNOT_BE_MODIFIED, ex.getErrorCode());
    }

    @Test
    void getPromotions_reportsReservedAndConfirmedCountsWithOneQuery() {
        Promotion promo = shippingPromo("SHIP20K", PromotionType.FIXED, "20000");
        when(promotionRepository.search(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(promo)));
        when(couponUsageRepository.countByPromotionIdsGroupedByStatus(List.of("promo-SHIP20K"))).thenReturn(List.of(
                new Object[] {"promo-SHIP20K", CouponUsageStatus.RESERVED, 2L},
                new Object[] {"promo-SHIP20K", CouponUsageStatus.CONFIRMED, 5L}));

        PromotionResponse response = promotionService.getPromotions(null, null, Pageable.unpaged()).getItems().get(0);

        assertEquals(2L, response.getReservedCount());
        assertEquals(5L, response.getConfirmedCount());
    }

    @Test
    void getPromotions_passesActiveAndTargetFiltersToTheRepository() {
        when(promotionRepository.search(eq(true), eq(PromotionTarget.SHIPPING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        promotionService.getPromotions(true, PromotionTarget.SHIPPING, Pageable.unpaged());

        verify(promotionRepository).search(eq(true), eq(PromotionTarget.SHIPPING), any(Pageable.class));
    }

    @Test
    void getPromotions_reportsTotalElementsForTheAdminPagingContract() {
        when(promotionRepository.search(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(shippingPromo("SHIP20K", PromotionType.FIXED, "20000"))));

        assertEquals(1L, promotionService.getPromotions(null, null, Pageable.unpaged()).getTotalElements());
    }

    @Test
    void listAvailable_keepsMinOrderMissesAsIneligibleAndDropsExhaustedCoupons() {
        Promotion usable = publicPromo("p-ok", "OK10", PromotionType.PERCENT, "10");
        Promotion tooSmall = publicPromo("p-big", "BIG", PromotionType.FIXED, "50000");
        tooSmall.setMinOrderValue(new BigDecimal("500000"));
        Promotion exhausted = publicPromo("p-gone", "GONE", PromotionType.FIXED, "1000");
        exhausted.setTotalQuota(1);
        when(promotionRepository.findPublicCurrentlyValid(any(), eq(PromotionTarget.PRODUCT)))
                .thenReturn(List.of(usable, tooSmall, exhausted));
        when(couponUsageRepository.countByPromotionIdAndStatusIn(eq("p-gone"), any())).thenReturn(1L);

        List<AvailablePromotionResponse> result = promotionService.listAvailable(
                "user-1", PromotionTarget.PRODUCT, new BigDecimal("200000"), List.of());

        assertEquals(2, result.size());
        assertTrue(result.get(0).eligible());
        assertEquals(0, result.get(0).estimatedDiscount().compareTo(new BigDecimal("20000")));
        assertFalse(result.get(1).eligible());
        assertEquals(OrderErrorCode.PROMOTION_MIN_ORDER_NOT_MET.getCode(), result.get(1).reasonCode());
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

        var response = promotionService.validatePromotionForCustomer("VALID10", "user-1", new BigDecimal("100000"), List.of());
        assertTrue(response.isValid());
        assertEquals("VALID10", response.getCode());
        assertEquals(0, response.getDiscountAmount().compareTo(new BigDecimal("10000")));
    }

    @Test
    void validatePromotionForCustomer_invalidCode_returnsInvalidResponse() {
        when(promotionRepository.findByCodeIgnoreCase("INVALID")).thenReturn(Optional.empty());

        var response = promotionService.validatePromotionForCustomer("INVALID", "user-1", new BigDecimal("100000"), List.of());
        assertFalse(response.isValid());
        assertEquals(0, response.getDiscountAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    void validatePromotionForCustomer_productScope_validWhenCartHasMatchingProduct() {
        Promotion promo = Promotion.builder()
                .code("PROD10")
                .type(PromotionType.PERCENT)
                .value(new BigDecimal("10"))
                .scopeType(PromotionScopeType.PRODUCT)
                .scopeIds("prod-1")
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .active(true)
                .build();
        when(promotionRepository.findByCodeIgnoreCase("PROD10")).thenReturn(Optional.of(promo));
        List<PromotionItemDto> items = List.of(
                PromotionItemDto.builder().productId("prod-1").lineTotal(new BigDecimal("100000")).build(),
                PromotionItemDto.builder().productId("prod-2").lineTotal(new BigDecimal("200000")).build());

        var response = promotionService.validatePromotionForCustomer("PROD10", "user-1", new BigDecimal("300000"), items);

        assertTrue(response.isValid());
        assertEquals(0, response.getDiscountAmount().compareTo(new BigDecimal("10000")));
    }

    @Test
    void validatePromotionForCustomer_perUserQuotaUsedUp_returnsInvalid() {
        Promotion promo = Promotion.builder()
                .code("ONCE")
                .type(PromotionType.FIXED)
                .value(new BigDecimal("20000"))
                .perUserQuota(1)
                .scopeType(PromotionScopeType.ALL)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .active(true)
                .build();
        promo.setId("promo-1");
        when(promotionRepository.findByCodeIgnoreCase("ONCE")).thenReturn(Optional.of(promo));
        when(couponUsageRepository.countByPromotionIdAndUserIdAndStatusIn(eq("promo-1"), eq("user-1"), any()))
                .thenReturn(1L);

        var response = promotionService.validatePromotionForCustomer("ONCE", "user-1", new BigDecimal("100000"), List.of());

        assertFalse(response.isValid());
    }
}
