package com.fashionstore.clothes_retail_api.modules.cart.entity;

import com.fashionstore.clothes_retail_api.common.entity.BaseEntity;
import com.fashionstore.clothes_retail_api.modules.product.entity.ProductVariant;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    ProductVariant variant;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    Integer quantity;
}
