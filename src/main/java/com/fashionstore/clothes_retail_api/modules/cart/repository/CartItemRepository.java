package com.fashionstore.clothes_retail_api.modules.cart.repository;

import com.fashionstore.clothes_retail_api.modules.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, String> {
    @EntityGraph(attributePaths = {"variant", "variant.product"})
    List<CartItem> findByCartId(String cartId);

    @EntityGraph(attributePaths = {"variant", "variant.product"})
    Optional<CartItem> findByCartIdAndVariantId(String cartId, String variantId);

    @EntityGraph(attributePaths = {"variant", "variant.product"})
    Optional<CartItem> findByIdAndCartId(String id, String cartId);

    void deleteByCartId(String cartId);
}
