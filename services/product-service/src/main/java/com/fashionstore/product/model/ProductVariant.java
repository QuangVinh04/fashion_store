package com.fashionstore.product.model;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@Entity
@Table(
        name = "product_variant",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_product_variant_sku", columnNames = "sku"),
                @UniqueConstraint(name = "uk_product_variant_product_signature", columnNames = {"product_id", "option_signature"})
        }
)
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    Product product;

    @Column(name = "option_signature", nullable = false, length = 255)
    String optionSignature; // build từ combination, vẫn giữ để check trùng nhanh

    @Column(name = "display_name", nullable = false, length = 255)
    String displayName; // "Đỏ / S" — build từ combination values

    String sku;
    String barcode;
    BigDecimal price;
    BigDecimal salePrice;

    @Builder.Default
    Boolean active = false;

    @Column(name = "thumbnail_media_id", length = 36)
    String thumbnailMediaId;

    @Column(name = "thumbnail_url")
    String thumbnailUrl;

}
