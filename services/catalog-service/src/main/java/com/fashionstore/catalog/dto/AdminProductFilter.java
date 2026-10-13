package com.fashionstore.catalog.dto;

import com.fashionstore.catalog.entity.enumeration.Gender;
import com.fashionstore.catalog.entity.enumeration.ProductStatus;
import com.fashionstore.catalog.entity.enumeration.ProductType;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminProductFilter {
    private String keyword;
    private ProductStatus status;
    private String categoryId;
    private String brandId;
    private Gender gender;
    private ProductType productType;
    @DecimalMin("0") private BigDecimal minPrice;
    @DecimalMin("0") private BigDecimal maxPrice;
}
