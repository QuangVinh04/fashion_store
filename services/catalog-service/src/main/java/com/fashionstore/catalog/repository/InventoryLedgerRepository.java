package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.entity.InventoryLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryLedgerRepository extends JpaRepository<InventoryLedger, String> {

    Page<InventoryLedger> findByVariantId(String variantId, Pageable pageable);
}
