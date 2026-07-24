package com.fashionstore.product.common.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    @Query("""
            select event from OutboxEvent event
            where event.status in ('PENDING', 'FAILED')
              and event.nextAttemptAt <= :now
            order by event.createdAt
            """)
    List<OutboxEvent> findPublishable(@Param("now") LocalDateTime now, Pageable pageable);
}
