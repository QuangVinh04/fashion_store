package com.fashionstore.order.cart.repository;

import com.fashionstore.order.cart.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, String> {

    Optional<CartItem> findByCartIdAndVariantId(String cartId, String variantId);

    @Query("SELECT ci FROM CartItem ci JOIN FETCH ci.cart WHERE ci.id = :id")
    Optional<CartItem> findByIdWithCart(String id);



    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId")
    void deleteByCartId(String cartId);

    @Modifying
    @Query("""
            delete from CartItem item
            where item.id in :itemIds
              and item.cart.userId = :userId
            """)
    int deleteOwnedItems(@Param("userId") String userId, @Param("itemIds") List<String> itemIds);
}
