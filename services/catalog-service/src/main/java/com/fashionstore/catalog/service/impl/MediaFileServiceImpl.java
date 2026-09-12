package com.fashionstore.catalog.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.catalog.config.FileStorageProperties;
import com.fashionstore.catalog.config.MinioProperties;
import com.fashionstore.catalog.dto.CompleteUploadRequest;
import com.fashionstore.catalog.dto.MediaFileResponse;
import com.fashionstore.catalog.dto.MediaFileUpdateRequest;
import com.fashionstore.catalog.dto.PresignUploadRequest;
import com.fashionstore.catalog.dto.PresignUploadResponse;
import com.fashionstore.catalog.dto.PresignedUpload;
import com.fashionstore.catalog.dto.StoredObject;
import com.fashionstore.catalog.exception.FileErrorCode;
import com.fashionstore.catalog.mapper.MediaFileMapper;
import com.fashionstore.catalog.entity.MediaFile;
import com.fashionstore.catalog.entity.enumeration.MediaStatus;
import com.fashionstore.catalog.entity.enumeration.MediaType;
import com.fashionstore.catalog.entity.enumeration.MediaVisibility;
import com.fashionstore.catalog.repository.MediaFileRepository;
import com.fashionstore.catalog.repository.MediaFileSpecifications;
import com.fashionstore.catalog.service.MediaFileService;
import com.fashionstore.catalog.service.StorageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MediaFileServiceImpl implements MediaFileService {

    /**
     * Browser PUT thang len storage nen service khong bao gio nhin thay bytes: content type
     * la thu duy nhat kiem soat duoc, va no cung la thu MinIO tra lai khi client tai file ve.
     * Cho phep text/html o day dong nghia mo cua XSS tren domain phuc vu media.
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/avif", "image/svg+xml",
            "video/mp4", "video/webm",
            "application/pdf");

    MediaFileRepository mediaFileRepository;
    StorageService storageService;
    CurrentUserProvider currentUserProvider;
    MediaFileMapper mediaFileMapper;
    FileStorageProperties storageProperties;
    MinioProperties minioProperties;

    @Override
    @Transactional
    public PresignUploadResponse presignUpload(PresignUploadRequest request) {
        String contentType = resolveContentType(request.getContentType());
        ensureContentTypeAllowed(contentType);
        ensureSizeWithinLimit(request.getSizeBytes());

        String originalFilename = cleanFilename(request.getFilename());
        String extension = resolveExtension(originalFilename);
        String storedFilename = UUID.randomUUID() + (extension == null ? "" : "." + extension);
        LocalDate today = LocalDate.now();
        String storageKey = "%d/%02d/%s".formatted(today.getYear(), today.getMonthValue(), storedFilename);

        MediaFile mediaFile = MediaFile.builder()
                .ownerId(currentUserProvider.getCurrentUserId())
                .originalFilename(originalFilename)
                .displayName(resolveDisplayName(request.getDisplayName(), originalFilename))
                .storedFilename(storedFilename)
                .storageKey(storageKey)
                .contentType(contentType)
                .extension(extension)
                .sizeBytes(request.getSizeBytes())
                .mediaType(resolveMediaType(contentType))
                .status(MediaStatus.PENDING)
                .visibility(request.getVisibility() == null ? MediaVisibility.PUBLIC : request.getVisibility())
                .altText(cleanNullable(request.getAltText()))
                .folder(cleanFolder(request.getFolder()))
                .tags(normalizeTags(request.getTags()))
                .build();

        MediaFile saved = mediaFileRepository.save(mediaFile);
        PresignedUpload presigned = storageService.presignUpload(storageKey, contentType);

        return PresignUploadResponse.builder()
                .mediaId(saved.getId())
                .storageKey(storageKey)
                .uploadUrl(presigned.url())
                .contentType(contentType)
                .expiresInSeconds(presigned.expiresInSeconds())
                .build();
    }

    @Override
    @Transactional
    public MediaFileResponse completeUpload(String id, CompleteUploadRequest request) {
        MediaFile mediaFile = findOwnedFile(id);
        if (mediaFile.getStatus() != MediaStatus.PENDING) {
            throw new AppException(FileErrorCode.FILE_ALREADY_COMPLETED);
        }

        // Nguon su that la storage, khong phai loi khai cua client o buoc presign.
        StoredObject stored = storageService.stat(mediaFile.getStorageKey());
        if (stored == null) {
            throw new AppException(FileErrorCode.FILE_UPLOAD_NOT_COMPLETED);
        }
        if (stored.sizeBytes() > minioProperties.maxUploadBytes()) {
            discard(mediaFile);
            throw new AppException(FileErrorCode.FILE_TOO_LARGE);
        }

        String contentType = resolveContentType(stored.contentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            discard(mediaFile);
            throw new AppException(FileErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        mediaFile.setContentType(contentType);
        mediaFile.setMediaType(resolveMediaType(contentType));
        mediaFile.setSizeBytes(stored.sizeBytes());
        mediaFile.setEtag(stored.etag());
        if (request != null) {
            mediaFile.setWidth(request.getWidth());
            mediaFile.setHeight(request.getHeight());
        }
        mediaFile.setStatus(MediaStatus.ACTIVE);

        return toResponse(mediaFileRepository.save(mediaFile));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<MediaFileResponse>> search(Pageable pageable,
                                                        String keyword,
                                                        MediaType mediaType,
                                                        String folder,
                                                        MediaStatus status) {
        String ownerId = currentUserProvider.getCurrentUserId();
        Page<MediaFile> page = mediaFileRepository.findAll(
                MediaFileSpecifications.filter(ownerId, keyword, mediaType, cleanFolder(folder), status),
                pageable
        );

        return PageResponse.<List<MediaFileResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(page.getTotalPages())
                .items(page.stream().map(this::toResponse).toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public MediaFileResponse getById(String id) {
        return toResponse(findOwnedFile(id));
    }

    @Override
    @Transactional
    public MediaFileResponse update(String id, MediaFileUpdateRequest request) {
        MediaFile mediaFile = findOwnedFile(id);
        if (request.getDisplayName() != null) {
            mediaFile.setDisplayName(resolveDisplayName(request.getDisplayName(), mediaFile.getOriginalFilename()));
        }
        if (request.getAltText() != null) {
            mediaFile.setAltText(cleanNullable(request.getAltText()));
        }
        if (request.getFolder() != null) {
            mediaFile.setFolder(cleanFolder(request.getFolder()));
        }
        if (request.getTags() != null) {
            mediaFile.setTags(normalizeTags(request.getTags()));
        }
        if (request.getVisibility() != null) {
            mediaFile.setVisibility(request.getVisibility());
        }
        return toResponse(mediaFileRepository.save(mediaFile));
    }

    @Override
    @Transactional
    public void moveToTrash(String id) {
        MediaFile mediaFile = findOwnedFile(id);
        if (mediaFile.getStatus() == MediaStatus.TRASHED) {
            throw new AppException(FileErrorCode.FILE_ALREADY_TRASHED);
        }
        mediaFile.setStatus(MediaStatus.TRASHED);
        mediaFile.setTrashedAt(LocalDateTime.now());
        mediaFileRepository.save(mediaFile);
    }

    @Override
    @Transactional
    public MediaFileResponse restore(String id) {
        MediaFile mediaFile = findOwnedFile(id);
        if (mediaFile.getStatus() != MediaStatus.TRASHED) {
            throw new AppException(FileErrorCode.FILE_NOT_TRASHED);
        }
        mediaFile.setStatus(MediaStatus.ACTIVE);
        mediaFile.setTrashedAt(null);
        return toResponse(mediaFileRepository.save(mediaFile));
    }

    @Override
    @Transactional
    public void deletePermanently(String id) {
        MediaFile mediaFile = findOwnedFile(id);
        storageService.delete(mediaFile.getStorageKey());
        mediaFileRepository.delete(mediaFile);
    }

    @Override
    @Transactional(readOnly = true)
    public String resolveContentUrl(String id) {
        MediaFile mediaFile = mediaFileRepository.findById(id)
                .orElseThrow(() -> new AppException(FileErrorCode.FILE_NOT_FOUND));
        ensureContentAccess(mediaFile);
        return storageService.presignDownload(mediaFile.getStorageKey());
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 0 * * * ?")
    public void purgeStalePendingUploads() {
        // Het han presign la het duong upload, nen row PENDING cu hon the khong bao gio
        // complete duoc nua. Nhan doi thoi han de mot lenh complete ve muon khong bi cat.
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(2L * minioProperties.presignExpirySeconds());
        List<MediaFile> stale = mediaFileRepository.findByStatusAndCreatedAtBefore(MediaStatus.PENDING, cutoff);
        for (MediaFile mediaFile : stale) {
            discard(mediaFile);
        }
        if (!stale.isEmpty()) {
            log.info("[Media] purged {} stale pending upload(s) older than {}", stale.size(), cutoff);
        }
    }

    /** Object co the da nam tren storage: xoa ca hai phia de khong de lai rac. */
    private void discard(MediaFile mediaFile) {
        storageService.delete(mediaFile.getStorageKey());
        mediaFileRepository.delete(mediaFile);
    }

    private MediaFile findOwnedFile(String id) {
        MediaFile mediaFile = mediaFileRepository.findById(id)
                .orElseThrow(() -> new AppException(FileErrorCode.FILE_NOT_FOUND));
        if (!mediaFile.getOwnerId().equals(currentUserProvider.getCurrentUserId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return mediaFile;
    }

    private void ensureContentAccess(MediaFile mediaFile) {
        if (mediaFile.getStatus() != MediaStatus.ACTIVE) {
            throw new AppException(FileErrorCode.FILE_NOT_FOUND);
        }
        if (mediaFile.getVisibility() == MediaVisibility.PUBLIC) {
            return;
        }
        String userId = getAuthenticatedUserIdOrNull();
        if (userId == null || !userId.equals(mediaFile.getOwnerId())) {
            throw new AppException(FileErrorCode.FILE_ACCESS_DENIED);
        }
    }

    private void ensureContentTypeAllowed(String contentType) {
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new AppException(FileErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    private void ensureSizeWithinLimit(Long sizeBytes) {
        if (sizeBytes == null || sizeBytes <= 0) {
            throw new AppException(FileErrorCode.FILE_UPLOAD_INVALID);
        }
        if (sizeBytes > minioProperties.maxUploadBytes()) {
            throw new AppException(FileErrorCode.FILE_TOO_LARGE);
        }
    }

    private String getAuthenticatedUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Jwt jwt = jwtAuthenticationToken.getToken();
            Object userId = jwt.getClaims().get("userId");
            if (userId != null && !userId.toString().isBlank()) {
                return userId.toString();
            }
            if (jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
                return jwt.getSubject();
            }
        }
        return authentication.getName();
    }

    private MediaFileResponse toResponse(MediaFile mediaFile) {
        return mediaFileMapper.toResponse(mediaFile, storageProperties.publicBaseUrl());
    }

    private String cleanFilename(String filename) {
        String cleaned = cleanNullable(filename);
        if (cleaned == null) {
            return "untitled";
        }
        String normalized = cleaned.replace("\\", "/");
        String base = normalized.substring(normalized.lastIndexOf('/') + 1);
        return base.isBlank() ? "untitled" : base;
    }

    private String resolveExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        String extension = filename.substring(dot + 1)
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
        return extension.isEmpty() ? null : extension;
    }

    private String resolveDisplayName(String displayName, String originalFilename) {
        String cleaned = cleanNullable(displayName);
        return cleaned == null ? originalFilename : cleaned;
    }

    private String cleanNullable(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String cleanFolder(String folder) {
        String cleaned = cleanNullable(folder);
        if (cleaned == null) {
            return null;
        }
        return cleaned.replace("\\", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "")
                .replaceAll("/{2,}", "/")
                .toLowerCase(Locale.ROOT);
    }

    private Set<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return tags.stream()
                .map(this::cleanNullable)
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String resolveContentType(String contentType) {
        String cleaned = cleanNullable(contentType);
        return cleaned == null ? "application/octet-stream" : cleaned.toLowerCase(Locale.ROOT);
    }

    private MediaType resolveMediaType(String contentType) {
        if (contentType.startsWith("image/")) {
            return MediaType.IMAGE;
        }
        if (contentType.startsWith("video/")) {
            return MediaType.VIDEO;
        }
        if (contentType.startsWith("audio/")) {
            return MediaType.AUDIO;
        }
        if (contentType.equals("application/pdf") || contentType.startsWith("text/")
                || contentType.contains("word") || contentType.contains("excel")
                || contentType.contains("spreadsheet") || contentType.contains("presentation")) {
            return MediaType.DOCUMENT;
        }
        return MediaType.OTHER;
    }
}
