package com.fashionstore.order.repository;

import com.fashionstore.order.model.OrderSaga;
import com.fashionstore.order.model.enumeration.OrderSagaStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderSagaRepository extends JpaRepository<OrderSaga, String> {

    /**
     * Reply từ nhiều participant có thể về cùng lúc, nên mọi handler đọc saga qua pessimistic lock.
     * Đây là điểm serialize duy nhất của saga — bảng orders không bị lock cho việc điều phối.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderSaga s where s.id = :id")
    Optional<OrderSaga> findByIdForUpdate(@Param("id") String id);

    Optional<OrderSaga> findByOrderId(String orderId);

    /**
     * Dùng cho luồng hủy đơn từ API. Khóa saga trước rồi mới khóa orders — đúng thứ tự mà handler saga
     * đang dùng, để một yêu cầu hủy và một reply về cùng lúc không khóa chéo nhau.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderSaga s where s.orderId = :orderId")
    Optional<OrderSaga> findByOrderIdForUpdate(@Param("orderId") String orderId);

    /**
     * Saga quá hạn ở bước hiện tại. {@code skip locked} để nhiều instance quét song song không giẫm chân nhau.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select s from OrderSaga s
             where s.status in :statuses
               and s.stepDeadline is not null
               and s.stepDeadline < :now
             order by s.stepDeadline asc
             limit 100
            """)
    List<OrderSaga> findDueForUpdate(
            @Param("statuses") List<OrderSagaStatus> statuses,
            @Param("now") LocalDateTime now
    );
}
