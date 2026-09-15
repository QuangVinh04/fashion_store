package com.fashionstore.order.entity;

import com.fashionstore.common.persistence.BaseEntity;
import com.fashionstore.order.entity.enumeration.CouponUsageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@Entity
@Table(
        name = "coupon_usage",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_coupon_usage_promo_user_order",
                columnNames = {"promotion_id", "user_id", "order_id"}
        )
)
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CouponUsage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    Promotion promotion;

    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    @Column(name = "order_id", nullable = false, unique = true, length = 36)
    String orderId;

    @Column(name = "used_at", nullable = false)
    LocalDateTime usedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    CouponUsageStatus status;
}
