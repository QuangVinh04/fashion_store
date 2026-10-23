package com.fashionstore.catalog.inventory.repository;

import com.fashionstore.catalog.inventory.entity.InventoryReservationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.fashionstore.catalog.inventory.entity.InventoryReservationStatus;

@Repository
public interface InventoryReservationItemRepository extends JpaRepository<InventoryReservationItem, String> {
    List<InventoryReservationItem> findByReservationId(String reservationId);

    @Query("""
            select item from InventoryReservationItem item, InventoryReservation reservation
            where item.reservationId = reservation.id and item.variantId = :variantId
              and (:status is null or reservation.status = :status)
            """)
    Page<InventoryReservationItem> findForAdmin(@Param("variantId") String variantId,
                                                 @Param("status") InventoryReservationStatus status,
                                                 Pageable pageable);
}
