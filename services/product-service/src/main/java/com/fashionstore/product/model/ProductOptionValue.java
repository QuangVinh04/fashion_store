package com.fashionstore.product.model;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@Entity
@Table(name = "product_option_value")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductOptionValue extends BaseEntity {


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_option_id", nullable = false)
    ProductOption productOption;

    @Column(nullable = false, length = 255)
    String value; // "Đỏ", "S"

    @Column(name = "normalized_value", nullable = false, length = 255)
    String normalizedValue; // chuẩn hóa cả dấu tiếng Việt — fix lỗ hổng đã bàn

    @Column(name = "color_hex", length = 7)
    String colorHex; // chỉ dùng nếu productOption.code = "COLOR"

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    Integer displayOrder = 0;

}
