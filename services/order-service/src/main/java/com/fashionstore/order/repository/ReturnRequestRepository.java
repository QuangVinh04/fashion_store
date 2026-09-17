package com.fashionstore.order.repository;

import com.fashionstore.order.entity.ReturnRequest;
import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;
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
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, String> {

    Optional<ReturnRequest> findByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequest r where r.id = :id")
    Optional<ReturnRequest> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequest r where r.order.id = :orderId")
    Optional<ReturnRequest> findByOrderIdForUpdate(@Param("orderId") String orderId);

    Page<ReturnRequest> findByStatus(ReturnRequestStatus status, Pageable pageable);

    Page<ReturnRequest> findByUserId(String userId, Pageable pageable);
}
