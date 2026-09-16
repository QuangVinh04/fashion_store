package com.fashionstore.order.repository;

import com.fashionstore.order.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, String> {
    Optional<Shipment> findByOrderId(String orderId);
    Optional<Shipment> findByGhnOrderCode(String ghnOrderCode);
    Optional<Shipment> findByTrackingCode(String trackingCode);
}
