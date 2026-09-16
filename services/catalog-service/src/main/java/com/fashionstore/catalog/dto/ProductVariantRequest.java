package com.fashionstore.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class ProductVariantRequest {
    String id;

    @NotBlank(message = "Size option id is required")
    String sizeOptionId;

    @NotBlank(message = "Color option id is required")
    String colorOptionId;

    String sku;

    String barcode;

    @DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative")
    BigDecimal price;

    @DecimalMin(value = "0.0", inclusive = true, message = "Sale price cannot be negative")
    BigDecimal salePrice;

    @Min(value = 0, message = "Weight cannot be negative")
    Integer weightGram;

    @Min(value = 0, message = "Length cannot be negative")
    Integer lengthMm;

    @Min(value = 0, message = "Width cannot be negative")
    Integer widthMm;

    @Min(value = 0, message = "Height cannot be negative")
    Integer heightMm;

    Boolean active;

    String thumbnailMediaId;

    String thumbnailUrl;

    String mediaId;
}
