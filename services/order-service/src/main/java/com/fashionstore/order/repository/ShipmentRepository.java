package com.fashionstore.order.repository;

import com.fashionstore.order.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, String> {
    @org.springframework.data.jpa.repository.Query("select s.order.id from Shipment s where s.ghnOrderCode=:code")
    java.util.Optional<String> findOrderIdByGhnOrderCode(@org.springframework.data.repository.query.Param("code") String code);
    @org.springframework.data.jpa.repository.Query("select s.order.id from Shipment s where s.trackingCode=:code")
    java.util.Optional<String> findOrderIdByTrackingCode(@org.springframework.data.repository.query.Param("code") String code);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Shipment s where s.order.id=:orderId")
    java.util.Optional<Shipment> findByOrderIdForUpdate(@org.springframework.data.repository.query.Param("orderId") String orderId);
    Optional<Shipment> findByOrderId(String orderId);
    Optional<Shipment> findByGhnOrderCode(String ghnOrderCode);
    Optional<Shipment> findByTrackingCode(String trackingCode);
}
