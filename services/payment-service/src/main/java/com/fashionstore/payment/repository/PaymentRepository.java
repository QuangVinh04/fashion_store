package com.fashionstore.payment.repository;

import com.fashionstore.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import com.fashionstore.payment.entity.enumeration.PaymentStatus;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByOrderId(String orderId);
    Optional<Payment> findByTransactionId(String transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.transactionId = :transactionId")
    Optional<Payment> findByTransactionIdForUpdate(@Param("transactionId") String transactionId);

    @Query("""
            select p from Payment p where p.status in :statuses and p.merchantReference is not null and p.paymentUrl is not null and p.method=com.fashionstore.common.payment.PaymentMethod.ONLINE
              and (p.lastReconciledAt is null or p.lastReconciledAt < :dueBefore)
              and (p.status = com.fashionstore.payment.entity.enumeration.PaymentStatus.PENDING
                   or p.authorizationExpiresAt > :closedAfter)
            order by p.lastReconciledAt asc nulls first, p.createdAt asc
            """)
    List<Payment> findForReconciliation(@Param("statuses") List<PaymentStatus> statuses,
                                       @Param("dueBefore") LocalDateTime dueBefore,
                                       @Param("closedAfter") java.time.Instant closedAfter, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.orderId = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByMerchantReference(String merchantReference);

    /** Read-only return-page lookup; callback processing retains its write lock above. */
    Optional<Payment> findForReturnByMerchantReference(String merchantReference);

    Optional<Payment> findByUserIdAndOperationId(String userId, String operationId);

    Optional<Payment> findByIdAndUserId(String id, String userId);

    org.springframework.data.domain.Page<Payment> findByUserIdAndTypeOrderByCreatedAtDesc(String userId, com.fashionstore.common.payment.PaymentType type, org.springframework.data.domain.Pageable pageable);
}
