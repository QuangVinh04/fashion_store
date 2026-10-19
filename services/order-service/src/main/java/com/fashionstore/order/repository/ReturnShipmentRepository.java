package com.fashionstore.order.repository;

import com.fashionstore.order.entity.ReturnShipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReturnShipmentRepository extends JpaRepository<ReturnShipment, String> {

    Optional<ReturnShipment> findByReturnRequestId(String returnRequestId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from ReturnShipment s where s.returnRequest.id = :returnRequestId")
    Optional<ReturnShipment> findByReturnRequestIdForUpdate(
            @org.springframework.data.repository.query.Param("returnRequestId") String returnRequestId);

    @org.springframework.data.jpa.repository.Query("select s.returnRequest.id from ReturnShipment s where s.ghnOrderCode = :code")
    Optional<String> findReturnRequestIdByGhnOrderCode(
            @org.springframework.data.repository.query.Param("code") String code);

    Optional<ReturnShipment> findByGhnOrderCode(String ghnOrderCode);

    /**
     * Đơn trả vẫn chờ GHN lấy hàng quá hạn (lượt lấy hàng đặt trước {@code cutoff}; dữ liệu cũ chưa có
     * mốc này thì tính từ ngày duyệt) và còn được thử hủy.
     */
    @org.springframework.data.jpa.repository.Query(
            "select s.returnRequest.id from ReturnShipment s"
                    + " where s.status = com.fashionstore.order.entity.enumeration.ReturnShipmentStatus.READY_TO_PICK"
                    + " and s.cancelAttempts < :maxAttempts"
                    + " and s.returnRequest.status in (com.fashionstore.order.entity.enumeration.ReturnRequestStatus.APPROVED,"
                    + " com.fashionstore.order.entity.enumeration.ReturnRequestStatus.RETURN_APPROVED)"
                    + " and coalesce(s.pickupRequestedAt, s.returnRequest.approvedAt) <= :cutoff"
                    + " order by coalesce(s.pickupRequestedAt, s.returnRequest.approvedAt)")
    java.util.List<String> findPickupOverdueReturnIds(
            @org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,
            @org.springframework.data.repository.query.Param("maxAttempts") int maxAttempts,
            org.springframework.data.domain.Pageable page);

    /** Vận đơn trả còn mở mà chưa đối soát với GHN từ {@code before} (webhook có thể đã bị lỡ). */
    @org.springframework.data.jpa.repository.Query(
            "select s.returnRequest.id from ReturnShipment s"
                    + " where s.status in (com.fashionstore.order.entity.enumeration.ReturnShipmentStatus.READY_TO_PICK,"
                    + " com.fashionstore.order.entity.enumeration.ReturnShipmentStatus.PICKED,"
                    + " com.fashionstore.order.entity.enumeration.ReturnShipmentStatus.NEEDS_ATTENTION)"
                    + " and s.returnRequest.closedAt is null"
                    + " and (s.lastReconciledAt is null or s.lastReconciledAt < :before)"
                    + " order by s.lastReconciledAt asc nulls first")
    java.util.List<String> findOpenForReconcile(
            @org.springframework.data.repository.query.Param("before") java.time.Instant before,
            org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.Query(
            "select count(s) from ReturnShipment s"
                    + " where s.status = com.fashionstore.order.entity.enumeration.ReturnShipmentStatus.PICKED"
                    + " and s.pickedAt < :cutoff and s.returnRequest.receivedAt is null and s.returnRequest.closedAt is null")
    long countInTransitSince(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);

    @org.springframework.data.jpa.repository.Query(
            "select count(s) from ReturnShipment s"
                    + " where s.status = com.fashionstore.order.entity.enumeration.ReturnShipmentStatus.ARRIVED"
                    + " and s.arrivedAt < :cutoff and s.returnRequest.receivedAt is null and s.returnRequest.closedAt is null")
    long countArrivedUnreceivedSince(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);

    @org.springframework.data.jpa.repository.Query(
            "select count(s) from ReturnShipment s"
                    + " where s.status = com.fashionstore.order.entity.enumeration.ReturnShipmentStatus.NEEDS_ATTENTION"
                    + " and s.returnRequest.closedAt is null")
    long countNeedingAttention();

    @org.springframework.data.jpa.repository.Query(
            "select count(s) from ReturnShipment s"
                    + " where s.status = com.fashionstore.order.entity.enumeration.ReturnShipmentStatus.READY_TO_PICK"
                    + " and s.cancelAttempts >= :maxAttempts and s.returnRequest.closedAt is null"
                    + " and coalesce(s.pickupRequestedAt, s.returnRequest.approvedAt) <= :cutoff")
    long countPickupCancelExhausted(
            @org.springframework.data.repository.query.Param("maxAttempts") int maxAttempts,
            @org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);
}
