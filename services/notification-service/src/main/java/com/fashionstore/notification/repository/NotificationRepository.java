package com.fashionstore.notification.repository;

import com.fashionstore.notification.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface NotificationRepository extends JpaRepository<Notification, String> {
    Page<Notification> findByUserId(String userId, Pageable pageable);
    long countByUserIdAndReadAtIsNull(String userId);
    boolean existsByIdAndUserId(String id, String userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.id = :id and n.userId = :userId and n.readAt is null")
    int markRead(@Param("id") String id, @Param("userId") String userId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") String userId, @Param("now") Instant now);
}
