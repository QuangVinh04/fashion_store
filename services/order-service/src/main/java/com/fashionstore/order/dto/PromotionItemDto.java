package com.fashionstore.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PromotionItemDto {
    private String variantId;
    private String productId;
    private String categoryId;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineTotal;
}
