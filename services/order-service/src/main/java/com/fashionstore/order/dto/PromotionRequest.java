package com.fashionstore.order.dto;

import com.fashionstore.order.entity.enumeration.PromotionScopeType;
import com.fashionstore.order.entity.enumeration.PromotionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class PromotionRequest {

    @NotBlank(message = "Promotion code is required")
    String code;

    @NotNull(message = "Promotion type is required")
    PromotionType type;

    @NotNull(message = "Promotion value is required")
    @Positive(message = "Promotion value must be positive")
    BigDecimal value;

    BigDecimal maxDiscount;

    BigDecimal minOrderValue;

    @NotNull(message = "Start time is required")
    LocalDateTime startAt;

    @NotNull(message = "End time is required")
    LocalDateTime endAt;

    Integer totalQuota;

    Integer perUserQuota;

    @NotNull(message = "Scope type is required")
    PromotionScopeType scopeType;

    String scopeIds;

    @Builder.Default
    Boolean active = true;
}
