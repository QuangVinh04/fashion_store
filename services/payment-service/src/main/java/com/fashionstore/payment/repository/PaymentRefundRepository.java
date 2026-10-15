package com.fashionstore.payment.repository;

import com.fashionstore.payment.entity.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

/** Provides refund idempotency and cumulative-refund queries. */
@Repository
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, String> {

    Optional<PaymentRefund> findByIdempotencyKey(String idempotencyKey);
    Optional<PaymentRefund> findByBusinessRefundId(String businessRefundId);
    org.springframework.data.domain.Page<PaymentRefund> findByPaymentIdOrderByCreatedAtDescIdDesc(String paymentId, org.springframework.data.domain.Pageable pageable);

    /** Until a failed/unknown provider refund is verified unpaid it keeps its refund budget. */
    @Query("select coalesce(sum(r.amount),0) from PaymentRefund r where r.payment.id=:paymentId and r.budgetReserved=true")
    BigDecimal sumReservedAmountByPaymentId(@Param("paymentId") String paymentId);
}
