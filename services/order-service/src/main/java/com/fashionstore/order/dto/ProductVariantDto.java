package com.fashionstore.order.dto;


import lombok.*;

import java.math.BigDecimal;

/** Mirror của catalog-service's ProductVariantSnapshotResponse — chỉ giữ field cart thật sự cần. */
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
    private BigDecimal salePrice;
    private Boolean active;

    /** true = chưa hỏi được catalog (fallback), khác hẳn với "catalog nói variant này không tồn tại". */
    private boolean upstreamUnavailable;

    /** Giá thực thu: có giá sale hợp lệ thì lấy giá sale, không thì giá gốc. */
    public BigDecimal effectivePrice() {
        if (salePrice == null || salePrice.signum() <= 0 || salePrice.compareTo(price) >= 0) {
            return price;
        }
        return salePrice;
    }

    // Factory: dùng trong fallback khi product-service down
    public static ProductVariantDto unavailable(String variantId) {
        return ProductVariantDto.builder()
                .variantId(variantId)
                .price(BigDecimal.ZERO)
                .upstreamUnavailable(true)
                .build();
    }
}
