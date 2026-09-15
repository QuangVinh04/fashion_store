package com.fashionstore.order.dto;

import com.fashionstore.order.entity.enumeration.PromotionScopeType;
import com.fashionstore.order.entity.enumeration.PromotionType;
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
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PromotionResponse {
    String id;
    String code;
    PromotionType type;
    BigDecimal value;
    BigDecimal maxDiscount;
    BigDecimal minOrderValue;
    LocalDateTime startAt;
    LocalDateTime endAt;
    Integer totalQuota;
    Integer perUserQuota;
    PromotionScopeType scopeType;
    String scopeIds;
    Boolean active;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
