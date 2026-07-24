package com.fashionstore.order.repository;

import com.fashionstore.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

import com.fashionstore.order.entity.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, String> {
    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findWithItemsById(String id);

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select orders from Order orders where orders.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Order> findByStatusAndUpdatedAtBefore(OrderStatus status, LocalDateTime cutoff);
}
