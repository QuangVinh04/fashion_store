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

    @Query("""
            select coalesce(sum(refund.amount), 0)
            from PaymentRefund refund
            where refund.payment.id = :paymentId
              and refund.status = com.fashionstore.payment.entity.enumeration.PaymentRefundStatus.COMPLETED
            """)
    BigDecimal sumCompletedAmountByPaymentId(@Param("paymentId") String paymentId);
}
