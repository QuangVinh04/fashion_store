package com.fashionstore.product.dto;

import com.fashionstore.product.model.enumeration.Gender;
import com.fashionstore.product.model.enumeration.ProductStatus;
import com.fashionstore.product.model.enumeration.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductRequest {
    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    String name;

    @Size(max = 255, message = "Slug must not exceed 255 characters")
    String slug;

    @Size(max = 500, message = "Short description must not exceed 500 characters")
    String shortDescription;

    String description;

    String brandId;

    ProductStatus status;

    Boolean published;

    Boolean featured;

    Gender gender;

    ProductType productType;

    @NonNull
    @DecimalMin(value = "0.0", inclusive = true, message = "Base price cannot be negative")
    BigDecimal basePrice;

    @DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative")
    BigDecimal price;

    @DecimalMin(value = "0.0", inclusive = true, message = "Sale price cannot be negative")
    BigDecimal salePrice;

    List<ProductImageItem> images;

    String sizeChartId;

    String metaTitle;

    String metaKeyword;

    String metaDescription;

    @NotEmpty(message = "At least one category is required")
    List<String> categoryIds;

    String categoryId;

    @Valid
    List<ProductVariantRequest> variants;

    List<String> colors;

    List<String> sizes;

    Map<String, String> colorHexMap;

}
