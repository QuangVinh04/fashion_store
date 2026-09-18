package com.fashionstore.order.repository;

import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.enumeration.OrderStatus;
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

    @Query("""
        select distinct o from Order o
        join o.items item
        where o.userId = :userId
          and o.status in (:statuses)
          and item.variantId in (:variantIds)
        order by o.createdAt desc
    """)
    java.util.List<Order> findEligibleOrdersForReview(
            @Param("userId") String userId,
            @Param("variantIds") java.util.List<String> variantIds,
            @Param("statuses") java.util.List<OrderStatus> statuses
    );

    @Query("""
        select distinct o from Order o
        join o.items item
        where o.id = :orderId
          and o.userId = :userId
          and o.status in (:statuses)
          and item.variantId in (:variantIds)
    """)
    Optional<Order> findEligibleOrderForReviewById(
            @Param("orderId") String orderId,
            @Param("userId") String userId,
            @Param("variantIds") java.util.List<String> variantIds,
            @Param("statuses") java.util.List<OrderStatus> statuses
    );

    @Query("SELECT new com.fashionstore.order.dto.dashboard.OrderStatusCountResponse(o.status, COUNT(o)) " +
           "FROM Order o GROUP BY o.status")
    java.util.List<com.fashionstore.order.dto.dashboard.OrderStatusCountResponse> countOrdersByStatus();

    @Query("SELECT CAST(o.createdAt AS LocalDate) as orderDate, SUM(o.totalAmount) as totalRevenue, COUNT(o) as orderCount " +
           "FROM Order o " +
           "WHERE o.status IN :validStatuses AND o.createdAt >= :startDate " +
           "GROUP BY CAST(o.createdAt AS LocalDate) " +
           "ORDER BY CAST(o.createdAt AS LocalDate) ASC")
    java.util.List<Object[]> findRevenueByDay(
            @Param("validStatuses") java.util.Collection<OrderStatus> validStatuses,
            @Param("startDate") java.time.LocalDateTime startDate
    );

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status IN :validStatuses")
    java.math.BigDecimal calculateTotalRevenue(@Param("validStatuses") java.util.Collection<OrderStatus> validStatuses);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status IN :validStatuses")
    long countOrdersByStatuses(@Param("validStatuses") java.util.Collection<OrderStatus> validStatuses);
}
