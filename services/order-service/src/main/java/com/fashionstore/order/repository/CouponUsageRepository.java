package com.fashionstore.order.repository;

import com.fashionstore.order.entity.CouponUsage;
import com.fashionstore.order.entity.enumeration.CouponUsageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, String> {

    long countByPromotionIdAndStatusIn(String promotionId, Collection<CouponUsageStatus> statuses);

    long countByPromotionIdAndUserIdAndStatusIn(String promotionId, String userId, Collection<CouponUsageStatus> statuses);

    Optional<CouponUsage> findByOrderId(String orderId);

    Optional<CouponUsage> findByPromotionIdAndOrderId(String promotionId, String orderId);
}
