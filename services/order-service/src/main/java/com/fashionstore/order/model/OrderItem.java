package com.fashionstore.order.model;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@Entity
@Table(name = "order_item")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    Order order;

    @Column(name = "cart_item_id", length = 36)
    String cartItemId;

    @Column(name = "variant_id", nullable = false)
    String variantId;

    @Column(name = "product_name", nullable = false, length = 255)
    String productName;

    @Column(name = "size_name", length = 20)
    String size;

    @Column(name = "color_name", length = 50)
    String color;

    @Column(nullable = false, precision = 19, scale = 2)
    BigDecimal unitPrice;

    @Column(nullable = false)
    Integer quantity;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    BigDecimal lineTotal;
}
