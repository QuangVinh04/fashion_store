package com.fashionstore.cart.repository;

import com.fashionstore.cart.model.Cart;
import com.fashionstore.cart.model.enumeration.CartStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, String> {

    @EntityGraph(attributePaths = {"items"})
    Optional<Cart> findByIdAndUserId(String id, String userId);

    @Query("""
            SELECT c FROM Cart c
            LEFT JOIN FETCH c.items
            WHERE c.id = :cartId
            """)
    Optional<Cart> findByIdWithItems(String cartId);


    @Query("""
            SELECT DISTINCT c FROM Cart c
            LEFT JOIN FETCH c.items
            WHERE c.userId = :userId AND c.status = :status
            """)
    Optional<Cart> findByUserIdAndStatus(@Param("userId") String userId,
                                     @Param("status") CartStatus status);





}
