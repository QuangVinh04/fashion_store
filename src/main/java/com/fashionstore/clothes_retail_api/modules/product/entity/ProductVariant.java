package com.fashionstore.clothes_retail_api.modules.product.entity;

import com.fashionstore.clothes_retail_api.common.entity.BaseEntity;
import com.fashionstore.clothes_retail_api.modules.category.entity.Category;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@Entity
@Table(name = "product_variant")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductVariant extends BaseEntity {

    @ManyToOne
    @JoinColumn(name="product_id", nullable=false)
    Product product;

    String size;

    String color;

    @Column(unique=true)
    String sku;

    @Column(nullable = false)
    BigDecimal price;

    @Column(nullable = false)
    Integer stock;


}
