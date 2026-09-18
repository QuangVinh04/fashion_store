package com.fashionstore.catalog.dto;

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