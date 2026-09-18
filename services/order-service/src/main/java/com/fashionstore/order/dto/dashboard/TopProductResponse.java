package com.fashionstore.order.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopProductResponse {
    private String variantId;
    private String productName;
    private Long totalQuantitySold;
    private BigDecimal totalRevenue;
}