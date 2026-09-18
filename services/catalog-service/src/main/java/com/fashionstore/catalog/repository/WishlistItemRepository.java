package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.entity.WishlistItem;
import com.fashionstore.catalog.entity.WishlistItemId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, WishlistItemId> {

    @EntityGraph(attributePaths = {"product", "product.brand"})
    Page<WishlistItem> findByIdUserId(String userId, Pageable pageable);

    boolean existsByIdUserIdAndIdProductId(String userId, String productId);

    void deleteByIdUserIdAndIdProductId(String userId, String productId);

    long countByIdUserId(String userId);
}
