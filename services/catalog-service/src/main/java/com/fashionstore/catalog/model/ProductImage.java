package com.fashionstore.catalog.model;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@Entity
@Table(name = "product_image")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    Product product;

    @Column(name = "media_id", nullable = false, length = 255)
    String mediaId; // tham chiếu tới MediaFile trong package media

    @Column(nullable = false, length = 1000)
    String url; // denormalize — tránh phải tra lại media mỗi lần hiển thị sản phẩm

    @Column(length = 100)
    String color; // nullable — null = ảnh chung, có giá trị = ảnh riêng theo màu

    @Column(name = "alt_text", length = 255)
    String altText;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    Integer sortOrder = 0;

    @Builder.Default
    @Column(name = "is_primary", nullable = false)
    Boolean isPrimary = false;
}
