package com.fashionstore.catalog.inventory.repository;

import com.fashionstore.catalog.inventory.entity.InventoryLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import com.fashionstore.catalog.inventory.entity.InventoryLedgerType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface InventoryLedgerRepository extends JpaRepository<InventoryLedger, String>, JpaSpecificationExecutor<InventoryLedger> {

    Page<InventoryLedger> findByVariantId(String variantId, Pageable pageable);

    Optional<InventoryLedger> findByOperationId(String operationId);
    boolean existsByRefOrderIdAndType(String orderId,InventoryLedgerType type);

    @Query(value = "select 1 from pg_advisory_xact_lock(hashtext(:operationId))", nativeQuery = true)
    Integer lockOperationId(@Param("operationId") String operationId);
}
