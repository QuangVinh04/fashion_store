package com.fashionstore.order.repository;

import com.fashionstore.order.entity.Checkout;
import com.fashionstore.order.entity.CheckoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CheckoutRepository extends JpaRepository<Checkout, String> {
    @EntityGraph(attributePaths = {"items"})
    List<Checkout> findByUserIdOrderByCreatedAtDesc(String userId);

    @EntityGraph(attributePaths = {"items"})
    Optional<Checkout> findByIdAndUserId(String id, String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"items", "order"})
    @Query("select c from Checkout c where c.id = :id and c.userId = :userId")
    Optional<Checkout> findForUpdateByIdAndUserId(@Param("id") String id, @Param("userId") String userId);

    @EntityGraph(attributePaths = {"items"})
    Optional<Checkout> findByOrderId(String orderId);
    Optional<Checkout> findByIdAndStatus(String id, CheckoutStatus status);
}
