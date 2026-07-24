package com.fashionstore.product.dto;

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
public class ProductVariantSnapshotResponse {
    String variantId;
    String productId;
    String productName;
    String sku;
    String barcode;
    String size;
    String color;
    String colorHex;
    BigDecimal price;
    BigDecimal salePrice;
    Boolean active;
    String optionSignature;
    String displayName;
}
