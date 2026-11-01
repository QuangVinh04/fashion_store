package com.fashionstore.catalog.wishlist.repository;

import com.fashionstore.catalog.wishlist.entity.WishlistItem;
import com.fashionstore.catalog.wishlist.entity.WishlistItemId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, WishlistItemId> {

    @EntityGraph(attributePaths = {"product", "product.brand"})
    Page<WishlistItem> findByIdUserId(String userId, Pageable pageable);

    boolean existsByIdUserIdAndIdProductId(String userId, String productId);

    @Query("select i.id.productId from WishlistItem i where i.id.userId = :userId and i.id.productId in :productIds")
    List<String> findWishlistedProductIds(@Param("userId") String userId, @Param("productIds") List<String> productIds);

    void deleteByIdUserIdAndIdProductId(String userId, String productId);

}
