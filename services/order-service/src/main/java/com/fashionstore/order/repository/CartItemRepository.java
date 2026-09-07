package com.fashionstore.order.repository;

import com.fashionstore.order.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, String> {

    Optional<CartItem> findByCartIdAndVariantId(String cartId, String variantId);

    @Query("SELECT ci FROM CartItem ci JOIN FETCH ci.cart WHERE ci.id = :id")
    Optional<CartItem> findByIdWithCart(String id);
}
