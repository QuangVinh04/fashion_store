package com.fashionstore.clothes_retail_api.modules.cart.repository;

import com.fashionstore.clothes_retail_api.modules.cart.entity.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, String> {

    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.product"})
    Optional<Cart> findByUserId(String userId);
}
