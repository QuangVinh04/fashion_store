package com.fashionstore.clothes_retail_api.modules.cart.entity;


import com.fashionstore.clothes_retail_api.common.entity.BaseEntity;
import com.fashionstore.clothes_retail_api.modules.auth.entity.User;
import jakarta.persistence.*;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@Entity
@Table(name = "cart")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Cart extends BaseEntity {


    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    User user;

    @Builder.Default
    @Column(name = "is_active")
    Boolean isActive = true;

    @Builder.Default
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    List<CartItem> items = new ArrayList<>();
}
