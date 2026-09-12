package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.entity.InventoryReservation;
import com.fashionstore.catalog.entity.enumeration.InventoryReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, String> {

    Optional<InventoryReservation> findByOrderId(String orderId);

    Optional<InventoryReservation> findByOrderIdAndStatus(String orderId, InventoryReservationStatus status);
}
