package com.fashionstore.cart.model;

import com.fashionstore.cart.model.enumeration.CartStatus;
import com.fashionstore.common.persistence.BaseEntity;
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

    @Column(name = "user_id", nullable = false, unique = true)
    String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    CartStatus status = CartStatus.ACTIVE;

    @Builder.Default
    @OneToMany(mappedBy = "cart", orphanRemoval = true, cascade = CascadeType.ALL)
    List<CartItem> items = new ArrayList<>();


    public void addItem(CartItem item) {
        items.add(item);
        item.setCart(this);
    }

    public void removeItem(CartItem item) {
        items.remove(item);
        item.setCart(null);
    }
}



