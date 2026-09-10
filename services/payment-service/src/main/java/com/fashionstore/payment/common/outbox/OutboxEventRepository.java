package com.fashionstore.payment.common.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event
            from OutboxEvent event
            where event.status in (
                com.fashionstore.common.messaging.outbox.OutboxEventStatus.PENDING,
                com.fashionstore.common.messaging.outbox.OutboxEventStatus.FAILED
            )
                and event.nextAttemptAt <= :now
            order by event.createdAt asc
            """)
    List<OutboxEvent> findPublishable(@Param("now") LocalDateTime now, Pageable pageable);
}
