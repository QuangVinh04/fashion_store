package com.fashionstore.order.cart.repository;

import com.fashionstore.order.cart.model.Cart;
import com.fashionstore.order.cart.model.enumeration.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, String> {

    /** {@code user_id} là unique nên mỗi user chỉ có đúng 1 dòng cart — không bao giờ có nhiều bản ghi. */
    @Query("""
            SELECT c FROM Cart c
            LEFT JOIN FETCH c.items
            WHERE c.userId = :userId AND c.status = :status
            """)
    Optional<Cart> findByUserIdAndStatus(@Param("userId") String userId,
                                     @Param("status") CartStatus status);
}
