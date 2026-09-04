package com.fashionstore.order.cart.dto.product;


import lombok.*;

import java.math.BigDecimal;

/** Mirror của product-service's ProductVariantSnapshotResponse — chỉ giữ field cart thật sự cần. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductVariantDto {
    private String variantId;
    private String productId;
    private String productName;
    private String size;
    private String color;
    private String sku;
    private BigDecimal price;
    private Boolean active;

    // Factory: dùng trong fallback khi product-service down
    public static ProductVariantDto unavailable(String variantId) {
        return ProductVariantDto.builder()
                .variantId(variantId)
                .price(BigDecimal.ZERO)
                .build();
    }
}
