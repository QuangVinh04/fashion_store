package com.fashionstore.product.model.option;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@Entity
@Table(name = "size_option")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SizeOption extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    String name; // "XXL"

    @Column(name = "normalized_name", nullable = false, unique = true, length = 50)
    String normalizedName;

    @Column(length = 50)
    String category; // "APPAREL", "SHOE", "KIDS" — nhóm size theo loại

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    Integer displayOrder = 0;

    @Builder.Default
    @Column(nullable = false)
    Boolean active = true;
}
