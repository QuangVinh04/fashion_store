package com.fashionstore.catalog.model.attribute;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
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
public class ProductAttributeOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_id", nullable = false)
    ProductAttribute attribute;

    @Column(nullable = false, length = 255)
    String value; // "Cotton 100%"

    @Column(name = "normalized_value", nullable = false, length = 255)
    String normalizedValue; // "COTTON_100" — check trùng

    @Builder.Default
    @Column(nullable = false)
    Boolean published = false;

}
