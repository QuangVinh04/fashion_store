package com.fashionstore.catalog.model;

import com.fashionstore.common.persistence.BaseEntity;
import com.fashionstore.catalog.model.enumeration.MediaStatus;
import com.fashionstore.catalog.model.enumeration.MediaType;
import com.fashionstore.catalog.model.enumeration.MediaVisibility;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@Entity
@Table(
        name = "media_file",
        indexes = {
                @Index(name = "idx_media_file_owner_status", columnList = "owner_id,status"),
                @Index(name = "idx_media_file_type", columnList = "media_type"),
                @Index(name = "idx_media_file_folder", columnList = "folder")
        }
)
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MediaFile extends BaseEntity {

    @Column(name = "owner_id", nullable = false, length = 64)
    String ownerId;

    @Column(name = "original_filename", nullable = false)
    String originalFilename;

    @Column(name = "display_name", nullable = false)
    String displayName;

    @Column(name = "stored_filename", nullable = false)
    String storedFilename;

    @Column(name = "storage_key", nullable = false, unique = true)
    String storageKey;

    @Column(name = "content_type", nullable = false, length = 120)
    String contentType;

    @Column(name = "extension", length = 20)
    String extension;

    @Column(name = "size_bytes", nullable = false)
    Long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    MediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    MediaStatus status = MediaStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    MediaVisibility visibility = MediaVisibility.PUBLIC;

    @Column(name = "alt_text")
    String altText;

    @Column(name = "folder")
    String folder;

    @Column(name = "width")
    Integer width;

    @Column(name = "height")
    Integer height;

    @Column(name = "trashed_at")
    LocalDateTime trashedAt;

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "media_file_tag", joinColumns = @JoinColumn(name = "file_id"))
    @Column(name = "tag", nullable = false, length = 80)
    Set<String> tags = new LinkedHashSet<>();
}
