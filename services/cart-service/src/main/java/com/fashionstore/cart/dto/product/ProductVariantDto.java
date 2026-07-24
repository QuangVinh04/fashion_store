package com.fashionstore.cart.dto.product;


import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductVariantDto {
    private String id;
    private String productId;
    private String productName;
    private String size;
    private String color;
    private String sku;
    private BigDecimal price;


    // Factory: dùng trong fallback khi product-service down
    public static ProductVariantDto unavailable(String variantId) {
        ProductVariantDto dto = new ProductVariantDto();
        dto.setId(variantId);
        dto.setPrice(BigDecimal.ZERO);
        return dto;
    }

}
