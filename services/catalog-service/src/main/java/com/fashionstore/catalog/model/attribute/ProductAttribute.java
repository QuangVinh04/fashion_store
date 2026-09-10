package com.fashionstore.catalog.model.attribute;

import com.fashionstore.common.persistence.BaseEntity;
import com.fashionstore.catalog.model.enumeration.AttributeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(
        name = "product_attribute"
)
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductAttribute extends BaseEntity {

    @Column(nullable = false, length = 100)
    String name;

    @Column(nullable = false, unique = true, length = 50)
    String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    AttributeType type;

    @Column(name = "display_name", length = 255)
    String displayName;

    @Builder.Default
    @Column(nullable = false)
    Boolean filterable = false;

    @Builder.Default
    @Column(nullable = false)
    Boolean searchable = false;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    Integer displayOrder = 0;

    @Builder.Default
    @Column(nullable = false)
    Boolean published = true;

}
