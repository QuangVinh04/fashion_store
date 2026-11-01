package com.fashionstore.order.promotion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PromotionItemDto {
    private String variantId;
    private String productId;
    private List<String> categoryIds;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineTotal;
}
