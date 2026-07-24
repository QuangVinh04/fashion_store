package com.fashionstore.product.dto;

import jakarta.validation.constraints.DecimalMin;
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

    String variantId;

    String size;

    String color;

    String colorHex;

    String sku;

    String barcode;

    @DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative")
    BigDecimal price;

    @DecimalMin(value = "0.0", inclusive = true, message = "Sale price cannot be negative")
    BigDecimal salePrice;

    Boolean active;

    String thumbnailMediaId;

    String thumbnailUrl;

    String mediaId;
    String imageUrl;
}
