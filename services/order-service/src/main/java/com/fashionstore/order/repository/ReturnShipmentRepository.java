package com.fashionstore.order.repository;

import com.fashionstore.order.entity.ReturnShipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReturnShipmentRepository extends JpaRepository<ReturnShipment, String> {

    Optional<ReturnShipment> findByReturnRequestId(String returnRequestId);

    Optional<ReturnShipment> findByGhnOrderCode(String ghnOrderCode);
}
