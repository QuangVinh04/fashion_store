package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.entity.InventoryReservation;
import com.fashionstore.catalog.entity.enumeration.InventoryReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, String> {

    Optional<InventoryReservation> findByOrderId(String orderId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from InventoryReservation r where r.orderId = :orderId")
    Optional<InventoryReservation> findByOrderIdForUpdate(@org.springframework.data.repository.query.Param("orderId") String orderId);

    Optional<InventoryReservation> findByOrderIdAndStatus(String orderId, InventoryReservationStatus status);
}
