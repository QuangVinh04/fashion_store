package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.model.MediaFile;
import com.fashionstore.catalog.model.enumeration.MediaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, String>, JpaSpecificationExecutor<MediaFile> {

    boolean existsByIdAndStatus(String id, MediaStatus status);

    List<MediaFile> findByStatusAndCreatedAtBefore(MediaStatus status, LocalDateTime createdBefore);
}
