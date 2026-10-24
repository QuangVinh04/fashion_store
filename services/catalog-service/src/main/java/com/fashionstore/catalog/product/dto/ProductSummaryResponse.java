package com.fashionstore.catalog.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fashionstore.catalog.product.entity.ProductStatus;
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
    Boolean featured;
    String thumbnailMediaId;
    String thumbnailUrl;
    String brandName;
    String categoryName;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    ProductStockSummaryResponse stock;
}
