package com.fashionstore.catalog.product.dto;

import com.fashionstore.catalog.product.entity.Gender;
import com.fashionstore.catalog.product.entity.ProductStatus;
import com.fashionstore.catalog.product.entity.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

/**
 * Defines the complete product aggregate accepted by the product creation API.
 */
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

    @DecimalMin(value = "0.0", inclusive = true, message = "Base price cannot be negative")
    BigDecimal basePrice;

    @DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative")
    BigDecimal price;

    @DecimalMin(value = "0.0", inclusive = true, message = "Sale price cannot be negative")
    BigDecimal salePrice;

    // Shipping parameters for logistics (GHN / GHTK: cm & gram)
    @Min(value = 1, message = "Weight must be greater than zero")
    @Max(50000)
    Integer weight;

    @Min(value = 1, message = "Length must be greater than zero")
    @Max(200)
    Integer length;

    @Min(value = 1, message = "Width must be greater than zero")
    @Max(200)
    Integer width;

    @Min(value = 1, message = "Height must be greater than zero")
    @Max(200)
    Integer height;

    // Legacy fields for backward compatibility
    @Min(value = 1, message = "Default weight must be greater than zero")
    @Max(50000)
    Integer weightGram;

    @Min(value = 1, message = "Default length must be greater than zero")
    @Max(2000)
    Integer lengthMm;

    @Min(value = 1, message = "Default width must be greater than zero")
    @Max(2000)
    Integer widthMm;

    @Min(value = 1, message = "Default height must be greater than zero")
    @Max(2000)
    Integer heightMm;

    List<ProductImageItem> images;

    String sizeChartId;

    @NotEmpty(message = "At least one category is required")
    List<String> categoryIds;

    String categoryId;

    @Valid
    @NotEmpty(message = "At least one product variant is required")
    List<ProductVariantRequest> variants;

    @Valid
    List<ProductAttributeValueRequest> attributes;

    public Integer getWeight() {
        return weight != null ? weight : weightGram;
    }

    public Integer getLength() {
        if (length != null) return length;
        return lengthMm != null ? (int) Math.ceil(lengthMm / 10.0) : null;
    }

    public Integer getWidth() {
        if (width != null) return width;
        return widthMm != null ? (int) Math.ceil(widthMm / 10.0) : null;
    }

    public Integer getHeight() {
        if (height != null) return height;
        return heightMm != null ? (int) Math.ceil(heightMm / 10.0) : null;
    }
}
