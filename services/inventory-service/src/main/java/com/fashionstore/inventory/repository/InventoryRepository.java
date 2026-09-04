package com.fashionstore.inventory.repository;


import com.fashionstore.inventory.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

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
}
