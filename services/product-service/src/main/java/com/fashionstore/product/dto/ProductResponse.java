package com.fashionstore.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fashionstore.product.model.enumeration.Gender;
import com.fashionstore.product.model.enumeration.ProductStatus;
import com.fashionstore.product.model.enumeration.ProductType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductResponse {
    String id;
    String name;
    String slug;
    String shortDescription;
    String description;
    BrandResponse brand;
    String brandId;
    ProductStatus status;
    Boolean published;
    LocalDateTime publishedAt;
    LocalDateTime deletedAt;
    Boolean featured;
    Gender gender;
    ProductType productType;
    BigDecimal basePrice;
    BigDecimal salePrice;
    BigDecimal price;
    String thumbnailMediaId;
    String sizeChartId;
    String metaTitle;
    String metaKeyword;
    String metaDescription;
    String categoryId;
    String categoryName;
    List<CategoryResponse> categories;
    List<ProductImageResponse> images;
    List<ProductVariantResponse> variants;
    List<ProductAttributeValueResponse> attributes;
}
