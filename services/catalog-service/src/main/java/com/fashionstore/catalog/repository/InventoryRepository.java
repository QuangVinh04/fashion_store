package com.fashionstore.catalog.repository;


import com.fashionstore.catalog.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import com.fashionstore.catalog.entity.enumeration.ProductStatus;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, String> {

    Optional<Inventory> findByVariantId(String variantId);

    List<Inventory> findByVariantIdIn(List<String> variantIds);

    List<Inventory> findByProductId(String productId);

    /**
     * Pessimistic lock — dùng khi reserve/release để tránh race condition.
     * Hai request cùng reserve cùng 1 variant → request sau phải chờ request trước commit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.variantId = :variantId")
    Optional<Inventory> findByVariantIdWithLock(String variantId);

    @Query("""
            select i from Inventory i, ProductVariant v join v.product p
            where v.id = i.variantId
              and (:includeInactive = true or (v.active = true and p.status = :published and p.deletedAt is null))
              and (:query is null or lower(coalesce(v.sku, '')) like :query or lower(p.name) like :query)
              and (:state = 'ALL'
                   or (:state = 'LOW' and i.quantity - i.reservedQuantity <= i.minThreshold)
                   or (:state = 'OUT' and i.quantity - i.reservedQuantity = 0))
            """)
    Page<Inventory> searchForAdmin(@Param("query") String query,
                                   @Param("state") String state,
                                   @Param("includeInactive") boolean includeInactive,
                                   @Param("published") ProductStatus published,
                                   Pageable pageable);

    @Query("""
            select i from Inventory i, ProductVariant v join v.product p
            where v.id = i.variantId and v.active = true and p.status = :published and p.deletedAt is null
              and i.quantity - i.reservedQuantity <= coalesce(:threshold, i.minThreshold)
            order by (i.quantity - i.reservedQuantity) asc
            """)
    Page<Inventory> findLowStockInventories(
            @Param("threshold") Integer threshold,
            @Param("published") ProductStatus published,
            Pageable pageable
    );

    @Query("""
            select count(i) from Inventory i, ProductVariant v join v.product p
            where v.id = i.variantId and v.active = true and p.status = :published and p.deletedAt is null
              and i.quantity - i.reservedQuantity <= coalesce(:threshold, i.minThreshold)
            """)
    long countLowStockInventories(@Param("threshold") Integer threshold,
                                  @Param("published") ProductStatus published);
}
