package com.fashionstore.order.cart.model;

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
