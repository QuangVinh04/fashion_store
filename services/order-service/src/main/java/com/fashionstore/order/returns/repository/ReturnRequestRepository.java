package com.fashionstore.order.returns.repository;

import com.fashionstore.order.returns.entity.ReturnRequest;
import com.fashionstore.order.returns.entity.ReturnRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, String>,org.springframework.data.jpa.repository.JpaSpecificationExecutor<ReturnRequest> {

    Optional<ReturnRequest> findByOrderId(String orderId);

    @Query("select r.order.id from ReturnRequest r where r.id = :id")
    Optional<String> findOrderIdByReturnId(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequest r where r.id = :id")
    Optional<ReturnRequest> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequest r where r.order.id = :orderId")
    Optional<ReturnRequest> findByOrderIdForUpdate(@Param("orderId") String orderId);

    /** Yêu cầu trả hàng của khách đã duyệt nhưng chưa nhận được hàng, duyệt trước {@code cutoff}. */
    @Query(
            "select r.id from ReturnRequest r"
                    + " where r.kind = com.fashionstore.order.returns.entity.ReturnRequestKind.CUSTOMER_RETURN"
                    + " and r.status in (com.fashionstore.order.returns.entity.ReturnRequestStatus.APPROVED,"
                    + " com.fashionstore.order.returns.entity.ReturnRequestStatus.RETURN_APPROVED)"
                    + " and r.receivedAt is null and r.closedAt is null and r.approvedAt <= :cutoff"
                    + " and not exists (select 1 from ReturnShipment s where s.returnRequest.id = r.id)")
    java.util.List<String> findUnsentCustomerReturnIds(@Param("cutoff") java.time.Instant cutoff);

    /** Yêu cầu chờ admin duyệt/từ chối quá hạn SLA (tạo trước {@code cutoff}). */
    @Query(
            "select count(r) from ReturnRequest r"
                    + " where r.kind = com.fashionstore.order.returns.entity.ReturnRequestKind.CUSTOMER_RETURN"
                    + " and r.status in (com.fashionstore.order.returns.entity.ReturnRequestStatus.REQUESTED,"
                    + " com.fashionstore.order.returns.entity.ReturnRequestStatus.PENDING)"
                    + " and r.createdAt < :cutoff")
    long countAwaitingReviewSince(@Param("cutoff") java.time.LocalDateTime cutoff);

    Page<ReturnRequest> findByStatus(ReturnRequestStatus status, Pageable pageable);

    Page<ReturnRequest> findByUserId(String userId, Pageable pageable);
}
