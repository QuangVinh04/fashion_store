package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.model.InventoryReservationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryReservationItemRepository extends JpaRepository<InventoryReservationItem, String> {
    List<InventoryReservationItem> findByReservationId(String reservationId);
    void deleteByReservationId(String reservationId);
}
