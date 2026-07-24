package com.fashionstore.clothes_retail_api.repository;

import com.fashionstore.clothes_retail_api.dto.inventory.*;
import com.fashionstore.clothes_retail_api.model.InventoryReservation;
import com.fashionstore.clothes_retail_api.model.InventoryReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;
import java.util.Optional;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, String> {

    List<InventoryReservation> findByOrderId(String orderId);

    List<InventoryReservation> findByOrderIdAndStatus(String orderId, InventoryReservationStatus status);

    Optional<InventoryReservation> findByOrderIdAndVariantId(String orderId, String variantId);

    boolean existsByOrderIdAndVariantIdAndStatus(String orderId, String variantId,
                                                 InventoryReservationStatus status);
}
