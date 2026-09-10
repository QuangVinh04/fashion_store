package com.fashionstore.catalog.model.option;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@Entity
@Table(name = "color_option")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ColorOption extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    String name; // "Đỏ"

    @Column(name = "normalized_name", nullable = false, unique = true, length = 100)
    String normalizedName; // "DO" — chuẩn hóa cả tiếng Việt không dấu

    @Column(name = "color_hex", length = 7)
    String colorHex; // "#CC0000"

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    Integer displayOrder = 0;

    @Builder.Default
    @Column(nullable = false)
    Boolean active = true;
}
