package com.fashionstore.order.repository;

import com.fashionstore.order.model.Checkout;
import com.fashionstore.order.model.enumeration.CheckoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CheckoutRepository extends JpaRepository<Checkout, String> {

    /**
     * Đánh dấu hết hạn cho checkout chưa sinh đơn. Cập nhật hàng loạt nên phải tự set updatedAt:
     * auditing của JPA không chạy với câu update trực tiếp.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update Checkout c
               set c.status = :expiredStatus,
                   c.expiredAt = :now,
                   c.updatedAt = :now
             where c.status in :openStatuses
               and c.order is null
               and c.createdAt < :cutoff
            """)
    int expireOpenCheckoutsCreatedBefore(
            @Param("expiredStatus") CheckoutStatus expiredStatus,
            @Param("openStatuses") List<CheckoutStatus> openStatuses,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now
    );
    @EntityGraph(attributePaths = {"items"})
    List<Checkout> findByUserIdOrderByCreatedAtDesc(String userId);

    @EntityGraph(attributePaths = {"items"})
    Optional<Checkout> findByIdAndUserId(String id, String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"items", "order"})
    @Query("select c from Checkout c where c.id = :id and c.userId = :userId")
    Optional<Checkout> findForUpdateByIdAndUserId(@Param("id") String id, @Param("userId") String userId);

}
