package com.fashionstore.product.dto;

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
