package com.fashionstore.catalog.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAdvanceSearchRequest {
    private com.fashionstore.catalog.product.entity.ProductType productType;
    private Integer minRating;
    private String[] search;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String size;
    private String color;
    private String brandId;
    private String gender;
    private String material;
    private String categoryId;
    private String keyword;
}
