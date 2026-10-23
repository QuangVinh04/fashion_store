package com.fashionstore.order.promotion.repository;

import com.fashionstore.order.promotion.entity.CouponUsage;
import com.fashionstore.order.promotion.entity.CouponUsageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, String> {

    long countByPromotionIdAndStatusIn(String promotionId, Collection<CouponUsageStatus> statuses);

    long countByPromotionIdAndUserIdAndStatusIn(String promotionId, String userId, Collection<CouponUsageStatus> statuses);

    Optional<CouponUsage> findByOrderId(String orderId);

}
