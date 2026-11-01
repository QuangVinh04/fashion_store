package com.fashionstore.order.promotion.repository;

import com.fashionstore.order.promotion.entity.CouponUsage;
import com.fashionstore.order.promotion.entity.CouponUsageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, String> {

    long countByPromotionIdAndStatusIn(String promotionId, Collection<CouponUsageStatus> statuses);

    long countByPromotionIdAndUserIdAndStatusIn(String promotionId, String userId, Collection<CouponUsageStatus> statuses);

    List<CouponUsage> findAllByOrderId(String orderId);

    boolean existsByOrderIdAndPromotionId(String orderId, String promotionId);

    @Query("select u.promotion.id, u.status, count(u) from CouponUsage u "
            + "where u.promotion.id in :promotionIds group by u.promotion.id, u.status")
    List<Object[]> countByPromotionIdsGroupedByStatus(@Param("promotionIds") Collection<String> promotionIds);

}
