package com.fashionstore.clothes_retail_api.modules.category.entity;


import com.fashionstore.clothes_retail_api.common.entity.BaseEntity;
import com.fashionstore.clothes_retail_api.modules.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Category extends BaseEntity {

    @Column(nullable = false, unique = true)
    String name;

    @Column(nullable = false, unique = true)
    String slug;

    @Column(columnDefinition="TEXT")
    String description;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    Category parent;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    List<Product> products;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    List<Category> children;
}
