package com.fashionstore.order.promotion.repository;

import com.fashionstore.order.promotion.dto.PromotionTarget;
import com.fashionstore.order.promotion.entity.CouponUsage;
import com.fashionstore.order.promotion.entity.CouponUsageStatus;
import com.fashionstore.order.promotion.entity.Promotion;
import com.fashionstore.order.promotion.entity.PromotionScopeType;
import com.fashionstore.order.promotion.entity.PromotionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // promotion.value is a keyword for H2 unless told otherwise
        "spring.datasource.url=jdbc:h2:mem:promotion;MODE=PostgreSQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PromotionRepositoryIntegrationTest {

    @Autowired
    PromotionRepository promotionRepository;

    @Autowired
    CouponUsageRepository couponUsageRepository;

    @Test
    void findPublicCurrentlyValid_returnsOnlyActivePublicPromotionsInsideTheirWindowForTheTarget() {
        LocalDateTime now = LocalDateTime.now();
        promotionRepository.save(promotion("PUBLIC-PRODUCT", PromotionTarget.PRODUCT, true, true, now.minusDays(1), now.plusDays(1)));
        promotionRepository.save(promotion("PUBLIC-SHIP", PromotionTarget.SHIPPING, true, true, now.minusDays(1), now.plusDays(1)));
        promotionRepository.save(promotion("HIDDEN", PromotionTarget.PRODUCT, false, true, now.minusDays(1), now.plusDays(1)));
        promotionRepository.save(promotion("INACTIVE", PromotionTarget.PRODUCT, true, false, now.minusDays(1), now.plusDays(1)));
        promotionRepository.save(promotion("EXPIRED", PromotionTarget.PRODUCT, true, true, now.minusDays(3), now.minusDays(1)));

        assertThat(promotionRepository.findPublicCurrentlyValid(now, PromotionTarget.PRODUCT))
                .extracting(Promotion::getCode).containsExactly("PUBLIC-PRODUCT");
        assertThat(promotionRepository.findPublicCurrentlyValid(now, null))
                .extracting(Promotion::getCode).containsExactlyInAnyOrder("PUBLIC-PRODUCT", "PUBLIC-SHIP");
    }

    @Test
    void search_filtersByActiveAndTargetAndIgnoresNullFilters() {
        LocalDateTime now = LocalDateTime.now();
        promotionRepository.save(promotion("P-ON", PromotionTarget.PRODUCT, true, true, now, now.plusDays(1)));
        promotionRepository.save(promotion("P-OFF", PromotionTarget.PRODUCT, true, false, now, now.plusDays(1)));
        promotionRepository.save(promotion("S-ON", PromotionTarget.SHIPPING, true, true, now, now.plusDays(1)));

        assertThat(promotionRepository.search(null, null, Pageable.unpaged()).getContent())
                .extracting(Promotion::getCode).containsExactlyInAnyOrder("P-ON", "P-OFF", "S-ON");
        assertThat(promotionRepository.search(null, PromotionTarget.SHIPPING, Pageable.unpaged()).getContent())
                .extracting(Promotion::getCode).containsExactly("S-ON");
        assertThat(promotionRepository.search(true, PromotionTarget.PRODUCT, Pageable.unpaged()).getContent())
                .extracting(Promotion::getCode).containsExactly("P-ON");
        assertThat(promotionRepository.search(false, null, Pageable.unpaged()).getContent())
                .extracting(Promotion::getCode).containsExactly("P-OFF");
    }

    @Test
    void anOrderCanHoldOneUsagePerPromotionAndCountsAreGroupedByStatus() {
        LocalDateTime now = LocalDateTime.now();
        Promotion product = promotionRepository.save(promotion("P", PromotionTarget.PRODUCT, true, true, now.minusDays(1), now.plusDays(1)));
        Promotion shipping = promotionRepository.save(promotion("S", PromotionTarget.SHIPPING, true, true, now.minusDays(1), now.plusDays(1)));
        couponUsageRepository.save(usage(product, "order-1", CouponUsageStatus.CONFIRMED));
        couponUsageRepository.save(usage(shipping, "order-1", CouponUsageStatus.CONFIRMED));
        couponUsageRepository.save(usage(shipping, "order-2", CouponUsageStatus.RESERVED));
        couponUsageRepository.flush();

        assertThat(couponUsageRepository.findAllByOrderId("order-1")).hasSize(2);
        assertThat(couponUsageRepository.existsByOrderIdAndPromotionId("order-1", shipping.getId())).isTrue();
        List<Object[]> rows = couponUsageRepository.countByPromotionIdsGroupedByStatus(List.of(product.getId(), shipping.getId()));
        assertThat(rows).extracting(row -> row[0] + ":" + row[1] + ":" + row[2]).containsExactlyInAnyOrder(
                product.getId() + ":CONFIRMED:1",
                shipping.getId() + ":CONFIRMED:1",
                shipping.getId() + ":RESERVED:1");
    }

    private Promotion promotion(String code, PromotionTarget target, boolean isPublic, boolean isActive,
                                LocalDateTime startAt, LocalDateTime endAt) {
        return Promotion.builder()
                .code(code)
                .target(target)
                .isPublic(isPublic)
                .active(isActive)
                .type(PromotionType.FIXED)
                .value(new BigDecimal("10000"))
                .scopeType(PromotionScopeType.ALL)
                .startAt(startAt)
                .endAt(endAt)
                .build();
    }

    private CouponUsage usage(Promotion promotion, String orderId, CouponUsageStatus status) {
        return CouponUsage.builder()
                .promotion(promotion)
                .userId("user-1")
                .orderId(orderId)
                .usedAt(LocalDateTime.now())
                .status(status)
                .build();
    }
}
