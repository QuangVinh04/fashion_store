package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.entity.OrderInventoryCancellation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OrderInventoryCancellationRepository extends JpaRepository<OrderInventoryCancellation,String> {
    Optional<OrderInventoryCancellation> findByOrderId(String orderId);
}
