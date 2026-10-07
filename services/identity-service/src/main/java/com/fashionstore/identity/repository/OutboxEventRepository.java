package com.fashionstore.identity.repository;

import com.fashionstore.common.messaging.outbox.OutboxEventStatus;
import com.fashionstore.identity.entity.OutboxEvent;
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
     * Lấy tối đa 50 dòng đến hạn gửi và khoá chúng. {@code skip locked} (lock timeout -2): dòng nào
     * instance khác đang gửi thì bỏ qua, nhờ vậy chạy nhiều replica cũng không gửi trùng.
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
