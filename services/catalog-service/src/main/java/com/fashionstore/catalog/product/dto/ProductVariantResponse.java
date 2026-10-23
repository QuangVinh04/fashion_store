package com.fashionstore.catalog.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class ProductVariantResponse {
    String id;
    String productId;
    String sku;
    String barcode;
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
    String thumbnailMediaId;
    String sizeOptionId;
    String colorOptionId;
    String size;
    String color;
    String colorHex;
}
