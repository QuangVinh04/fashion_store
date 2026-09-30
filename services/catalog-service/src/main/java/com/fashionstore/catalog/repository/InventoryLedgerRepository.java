package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.entity.InventoryLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;
import com.fashionstore.catalog.entity.enumeration.InventoryLedgerType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface InventoryLedgerRepository extends JpaRepository<InventoryLedger, String> {

    Page<InventoryLedger> findByVariantId(String variantId, Pageable pageable);

    Optional<InventoryLedger> findByOperationId(String operationId);

    @Query(value = "select 1 from pg_advisory_xact_lock(hashtext(:operationId))", nativeQuery = true)
    Integer lockOperationId(@Param("operationId") String operationId);

    @Query("""
            select l from InventoryLedger l
            where (:variantId is null or l.variantId = :variantId)
              and (:type is null or l.type = :type)
              and (:fromTime is null or l.createdAt >= :fromTime)
              and (:toTime is null or l.createdAt <= :toTime)
            """)
    Page<InventoryLedger> search(@Param("variantId") String variantId,
                                 @Param("type") InventoryLedgerType type,
                                 @Param("fromTime") LocalDateTime fromTime,
                                 @Param("toTime") LocalDateTime toTime,
                                 Pageable pageable);
}
