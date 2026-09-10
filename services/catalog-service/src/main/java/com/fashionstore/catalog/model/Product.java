package com.fashionstore.catalog.model;

import com.fashionstore.common.persistence.BaseEntity;
import com.fashionstore.catalog.model.attribute.ProductAttributeValue;
import com.fashionstore.catalog.model.enumeration.Gender;
import com.fashionstore.catalog.model.enumeration.ProductStatus;
import com.fashionstore.catalog.model.enumeration.ProductType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@Entity
@Table(name = "product")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Product extends BaseEntity {

    @Column(nullable = false, length = 255)
    String name;

    @Column(nullable = false, unique = true, length = 255)
    String slug;

    @Column(name = "short_description", length = 500)
    String shortDescription;

    @Column(columnDefinition = "TEXT")
    String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    Brand brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    ProductStatus status = ProductStatus.DRAFT;

    @Column(nullable = false)
    @Builder.Default
    Boolean published = false;

    @Column(name = "published_at")
    LocalDateTime publishedAt;

    @Column(name = "deleted_at")
    LocalDateTime deletedAt;

    @Column(nullable = false)
    @Builder.Default
    Boolean featured = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", length = 50)
    ProductType productType;

    @Column(name = "base_price", precision = 12, scale = 2)
    BigDecimal basePrice;

    @Column(name = "sale_price", precision = 12, scale = 2)
    BigDecimal salePrice;

    @Column(name = "thumbnail_media_id", length = 36)
    String thumbnailMediaId;

    @Column(name = "thumbnail_url")
    String thumbnailUrl;

    @Column(name = "size_chart_id", length = 36)
    String sizeChartId;

    @Column(name = "meta_title", length = 255)
    String metaTitle;

    @Column(name = "meta_keyword", length = 500)
    String metaKeyword;

    @Column(name = "meta_description", length = 500)
    String metaDescription;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<ProductCategory> productCategories = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<ProductAttributeValue> attributeValues = new ArrayList<>();

    public BigDecimal getPrice() {
        return basePrice;
    }

    public void setPrice(BigDecimal price) {
        this.basePrice = price;
    }
}
