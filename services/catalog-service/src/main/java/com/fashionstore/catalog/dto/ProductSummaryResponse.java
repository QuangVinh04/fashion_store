package com.fashionstore.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fashionstore.catalog.model.enumeration.ProductStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductSummaryResponse {
    String id;
    String name;
    String slug;
    BigDecimal basePrice;
    BigDecimal salePrice;
    BigDecimal price;
    ProductStatus status;
    Boolean published;
    String thumbnailMediaId;
    String brandName;
    String categoryName;
}
