package com.fashionstore.order.repository;

import com.fashionstore.order.model.Order;
import com.fashionstore.order.model.enumeration.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findWithItemsById(String id);

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select orders from Order orders where orders.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") String id);

    // Danh sách trả về bản rút gọn nên cố tình không nạp items: nạp collection cùng phân trang
    // sẽ khiến Hibernate phân trang trong bộ nhớ.

    Page<Order> findByUserId(String userId, Pageable pageable);

    Page<Order> findByUserIdAndStatus(String userId, OrderStatus status, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
}
