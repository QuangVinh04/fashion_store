package com.fashionstore.catalog.product.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductVariantSnapshotResponse {
    String variantId;
    String productId;
    String productName;
    String sku;
    String size;
    String color;
    String colorHex;
    List<String> categoryIds;
    BigDecimal price;
    BigDecimal salePrice;

    // Shipping parameters for logistics (GHN / GHTK: cm & gram)
    Integer weight;
    Integer length;
    Integer width;
    Integer height;

    // Legacy fields for backward compatibility
    Integer weightGram;
    Integer lengthMm;
    Integer widthMm;
    Integer heightMm;

    Boolean active;
    String optionSignature;
    String displayName;
}
