package com.fashionstore.order.repository;

import com.fashionstore.order.entity.OrderSaga;
import com.fashionstore.order.entity.enumeration.OrderSagaStatus;
import com.fashionstore.order.entity.enumeration.OrderSagaType;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderSagaRepository extends JpaRepository<OrderSaga, String> {

    List<SagaTimeoutCandidate> findDueCandidates(
            @Param("statuses") List<OrderSagaStatus> statuses, @Param("now") LocalDateTime now);

    /**
     * Reply từ nhiều participant có thể về cùng lúc, nên mọi handler đọc saga qua pessimistic lock.
     * Caller luôn khóa Order trước Saga để các luồng không khóa chéo nhau.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderSaga s where s.id = :id")
    Optional<OrderSaga> findByIdForUpdate(@Param("id") String id);

    Optional<OrderSaga> findByOrderIdAndType(String orderId, OrderSagaType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderSaga s where s.orderId = :orderId and s.type = :type")
    Optional<OrderSaga> findByOrderIdAndTypeForUpdate(
            @Param("orderId") String orderId, @Param("type") OrderSagaType type);
}
