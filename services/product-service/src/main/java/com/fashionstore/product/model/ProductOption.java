package com.fashionstore.product.model;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@Entity
@Table(name = "product_option")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductOption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(nullable = false, unique = true, length = 100)
    String name; // "Color", "Size" — CHỈ 2 record này tồn tại, seed sẵn

    @Column(nullable = false, unique = true, length = 50)
    String code; // "COLOR", "SIZE"

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    Integer displayOrder = 0;
}
