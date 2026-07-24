package com.fashionstore.file.repository;

import com.fashionstore.file.model.MediaFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, String>, JpaSpecificationExecutor<MediaFile> {
}
