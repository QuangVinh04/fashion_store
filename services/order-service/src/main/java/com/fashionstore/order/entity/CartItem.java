package com.fashionstore.order.entity;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@Entity
@Table(
        name = "cart_item",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cart_id", "variant_id"})
)
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CartItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    Cart cart;

    @Column(name = "variant_id", nullable = false)
    String variantId;

    @Column(name = "product_id", nullable = false)
    String productId;

    /**
     * Snapshot lúc thêm vào giỏ, không phải cache: checkout_item / order_item bắt buộc có tên, nên tên
     * không thể phụ thuộc vào việc catalog có trả lời được lúc bấm đặt hàng hay không.
     */
    @Column(name = "product_name", length = 255)
    String productName;


    @Column(name = "size_name", length = 20)
    String size;

    @Column(name = "color_name", length = 50)
    String color;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    BigDecimal unitPrice;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    Integer quantity;
}
