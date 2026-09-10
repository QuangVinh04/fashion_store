package com.fashionstore.order.outbox;

import com.fashionstore.common.messaging.outbox.OutboxEventStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    /**
     * Lưới an toàn cho message bị đọng do app crash hoặc Rabbit chết lúc gửi.
     * {@code skip locked} để nhiều instance quét song song không tranh nhau cùng một dòng.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select e from OutboxEvent e
             where e.status = :status
               and e.nextAttemptAt <= :now
             order by e.nextAttemptAt asc
             limit 50
            """)
    List<OutboxEvent> findBatchToPublish(
            @Param("status") OutboxEventStatus status,
            @Param("now") LocalDateTime now
    );

    void deleteByStatusAndPublishedAtBefore(OutboxEventStatus status, LocalDateTime publishedAt);
}
