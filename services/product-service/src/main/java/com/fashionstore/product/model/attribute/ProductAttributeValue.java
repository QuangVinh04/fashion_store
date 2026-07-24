package com.fashionstore.product.model.attribute;

import com.fashionstore.common.persistence.BaseEntity;
import com.fashionstore.product.model.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "product_attribute_value")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductAttributeValue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_id", nullable = false)
    ProductAttribute attribute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_option_id")
    ProductAttributeOption attributeOption;

    @Column(nullable = false, length = 255)
    String value;

    @Column(name = "normalized_value", nullable = false, length = 255)
    String normalizedValue;

    @Builder.Default
    @Column(nullable = false)
    Integer position = 0;

    @Builder.Default
    @Column(nullable = false)
    Boolean published = false;

    @PrePersist
    @PreUpdate
    void normalizeBeforeSave() {
        if ((normalizedValue == null || normalizedValue.isBlank()) && value != null) {
            normalizedValue = value.trim().replaceAll("\\s+", "_").toUpperCase();
        }
        if (position == null) {
            position = 0;
        }
    }
}
