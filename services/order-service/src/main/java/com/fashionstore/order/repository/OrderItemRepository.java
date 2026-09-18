package com.fashionstore.order.repository;

import com.fashionstore.order.dto.dashboard.TopProductResponse;
import com.fashionstore.order.entity.OrderItem;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, String> {

    @Query("SELECT new com.fashionstore.order.dto.dashboard.TopProductResponse(" +
           "oi.variantId, oi.productName, SUM(CAST(oi.quantity as long)), SUM(oi.lineTotal)) " +
           "FROM OrderItem oi " +
           "JOIN oi.order o " +
           "WHERE o.status IN :validStatuses " +
           "GROUP BY oi.variantId, oi.productName " +
           "ORDER BY SUM(CAST(oi.quantity as long)) DESC")
    List<TopProductResponse> findTopSellingProducts(
            @Param("validStatuses") Collection<OrderStatus> validStatuses,
            Pageable pageable
    );
}