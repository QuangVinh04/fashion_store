package com.fashionstore.identity.repository;

import com.fashionstore.identity.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    boolean existsByEventId(String eventId);

    // cast(... as string): Postgres không suy được kiểu của tham số null trong "? is null"
    @Query("""
            select a from AuditLog a
            where (cast(:actorId as string) is null or a.actorId = :actorId)
              and (cast(:entityType as string) is null or a.entityType = :entityType)
              and (cast(:entityId as string) is null or a.entityId = :entityId)
              and (cast(:action as string) is null or a.action = :action)
            """)
    Page<AuditLog> search(@Param("actorId") String actorId,
                          @Param("entityType") String entityType,
                          @Param("entityId") String entityId,
                          @Param("action") String action,
                          Pageable pageable);
}
