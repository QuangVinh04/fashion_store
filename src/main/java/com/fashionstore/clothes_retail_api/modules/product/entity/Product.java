package com.fashionstore.clothes_retail_api.modules.product.entity;


import com.fashionstore.clothes_retail_api.common.entity.BaseEntity;
import com.fashionstore.clothes_retail_api.modules.category.entity.Category;
import jakarta.persistence.*;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@Entity
@Table(name = "product")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Product extends BaseEntity {

    @Column(nullable = false, length = 200)
    String name;

    @Column(columnDefinition="TEXT")
    String description;

    @Column(nullable = false)
    BigDecimal price;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    Category category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    List<ProductVariant> variants = new ArrayList<>();

}
