package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.entity.MediaFile;
import com.fashionstore.catalog.entity.enumeration.MediaPurpose;
import com.fashionstore.catalog.entity.enumeration.MediaStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, String>, JpaSpecificationExecutor<MediaFile> {

    boolean existsByIdAndStatus(String id, MediaStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MediaFile m WHERE m.id = :id")
    Optional<MediaFile> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<MediaFile> findByOwnerIdAndPurposeOrderByIdAsc(String ownerId, MediaPurpose purpose);

    @Query("SELECT m.ownerId FROM MediaFile m WHERE m.id = :id")
    Optional<String> findOwnerIdById(@Param("id") String id);

    @Query("SELECT m.id FROM MediaFile m WHERE m.status = com.fashionstore.catalog.entity.enumeration.MediaStatus.PENDING AND m.createdAt < :cutoff ORDER BY m.id")
    List<String> findPendingIdsBefore(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    @Query("""
            SELECT m.id FROM MediaFile m
            WHERE m.purpose = com.fashionstore.catalog.entity.enumeration.MediaPurpose.AVATAR
            AND m.id > :afterId
            AND ((m.status = com.fashionstore.catalog.entity.enumeration.MediaStatus.TEMP AND m.createdAt < :readyBefore)
              OR (m.status = com.fashionstore.catalog.entity.enumeration.MediaStatus.ACTIVE AND m.retiredAt < :retiredBefore)
              OR (m.status = com.fashionstore.catalog.entity.enumeration.MediaStatus.TRASHED AND m.createdAt < :pendingBefore))
            ORDER BY m.id
            """)
    List<String> findReconciliationIds(@Param("readyBefore") LocalDateTime readyBefore,
                                       @Param("retiredBefore") LocalDateTime retiredBefore,
                                       @Param("pendingBefore") LocalDateTime pendingBefore, @Param("afterId") String afterId, Pageable pageable);
}
