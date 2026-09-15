package com.fashionstore.order.entity;

import com.fashionstore.common.persistence.BaseEntity;
import com.fashionstore.order.entity.enumeration.PromotionScopeType;
import com.fashionstore.order.entity.enumeration.PromotionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@Entity
@Table(name = "promotion")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Promotion extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    PromotionType type;

    @Column(name = "value", nullable = false, precision = 19, scale = 2)
    BigDecimal value;

    @Column(name = "max_discount", precision = 19, scale = 2)
    BigDecimal maxDiscount;

    @Column(name = "min_order_value", precision = 19, scale = 2)
    BigDecimal minOrderValue;

    @Column(name = "start_at", nullable = false)
    LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    LocalDateTime endAt;

    @Column(name = "total_quota")
    Integer totalQuota;

    @Column(name = "per_user_quota")
    Integer perUserQuota;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    PromotionScopeType scopeType;

    @Column(name = "scope_ids", columnDefinition = "text")
    String scopeIds;

    @Builder.Default
    @Column(name = "active", nullable = false)
    Boolean active = true;
}
