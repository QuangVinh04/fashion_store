package com.fashionstore.catalog.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.catalog.config.FileStorageProperties;
import com.fashionstore.catalog.client.IdentityAvatarClient;
import com.fashionstore.contracts.identity.event.ProfileAvatarChangedEvent;
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
import com.fashionstore.catalog.entity.enumeration.MediaPurpose;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MediaFileServiceImpl implements MediaFileService {

    /** Generic files use storage metadata validation; avatars also verify the image signature. */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/avif", "image/svg+xml",
            "video/mp4", "video/webm",
            "application/pdf");

    public static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;
    private static final Set<String> AVATAR_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    IdentityAvatarClient identityAvatarClient;
    MediaFileRepository mediaFileRepository;
    StorageService storageService;
    CurrentUserProvider currentUserProvider;
    MediaFileMapper mediaFileMapper;
    FileStorageProperties storageProperties;
    MinioProperties minioProperties;

    @Override
    @Transactional
    public PresignUploadResponse presignUpload(PresignUploadRequest request) {
        return presignUpload(request, MediaPurpose.GENERAL);
    }

    @Override
    @Transactional
    public PresignUploadResponse presignUpload(PresignUploadRequest request, MediaPurpose purpose) {
        boolean avatar = purpose == MediaPurpose.AVATAR;
        String contentType = resolveContentType(request.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new AppException(FileErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        Long sizeBytes = request.getSizeBytes();
        if (sizeBytes == null || sizeBytes <= 0) {
            throw new AppException(FileErrorCode.FILE_UPLOAD_INVALID);
        }
        if (sizeBytes > minioProperties.maxUploadBytes()) {
            throw new AppException(FileErrorCode.FILE_TOO_LARGE);
        }
        if (avatar && !AVATAR_CONTENT_TYPES.contains(contentType)) {
            throw new AppException(FileErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        if (avatar && sizeBytes > MAX_AVATAR_BYTES) {
            throw new AppException(FileErrorCode.FILE_TOO_LARGE);
        }

        String originalFilename = cleanNullable(request.getFilename());
        if (originalFilename != null) {
            originalFilename = originalFilename.replace("\\", "/");
            originalFilename = originalFilename.substring(originalFilename.lastIndexOf('/') + 1);
        }
        if (originalFilename == null || originalFilename.isBlank()) originalFilename = "untitled";
        int dot = originalFilename.lastIndexOf('.');
        String extension = dot < 0 ? null : cleanNullable(originalFilename.substring(dot + 1)
                .replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT));
        String storedFilename = UUID.randomUUID() + (extension == null ? "" : "." + extension);
        LocalDate today = LocalDate.now();
        String storageKey = (avatar ? "avatars/" : "") + "%d/%02d/%s".formatted(today.getYear(), today.getMonthValue(), storedFilename);

        MediaFile mediaFile = MediaFile.builder()
                .ownerId(currentUserProvider.getCurrentUserId())
                .originalFilename(originalFilename)
                .displayName(resolveDisplayName(request.getDisplayName(), originalFilename))
                .storedFilename(storedFilename)
                .storageKey(storageKey)
                .contentType(contentType)
                .extension(extension)
                .sizeBytes(sizeBytes)
                .mediaType(resolveMediaType(contentType))
                .status(MediaStatus.PENDING)
                .purpose(purpose)
                .expiresAt(avatar ? LocalDateTime.now().plusHours(24) : null)
                .visibility(avatar ? MediaVisibility.PRIVATE : (request.getVisibility() == null ? MediaVisibility.PUBLIC : request.getVisibility()))
                .altText(cleanNullable(request.getAltText()))
                .folder(avatar ? "avatars" : cleanFolder(request.getFolder()))
                .tags(normalizeTags(request.getTags()))
                .build();

        MediaFile saved = mediaFileRepository.save(mediaFile);
        Map<String, String> headers = avatar ? Map.of("Content-Type", contentType, "If-None-Match", "*") : Map.of("Content-Type", contentType);
        PresignedUpload presigned = avatar ? storageService.presignUpload(storageKey, contentType, headers)
                : storageService.presignUpload(storageKey, contentType);

        return PresignUploadResponse.builder()
                .mediaId(saved.getId())
                .storageKey(storageKey)
                .uploadUrl(presigned.url())
                .uploadHeaders(headers)
                .contentType(contentType)
                .expiresInSeconds(presigned.expiresInSeconds())
                .build();
    }

    @Override
    @Transactional(noRollbackFor = AppException.class)
    public MediaFileResponse completeUpload(String id, CompleteUploadRequest request) {
        MediaFile mediaFile = mediaFileRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(FileErrorCode.FILE_NOT_FOUND));
        ensureOwner(mediaFile, currentUserProvider.getCurrentUserId());
        boolean avatar = mediaFile.getPurpose() == MediaPurpose.AVATAR;
        if (avatar && mediaFile.getStatus() == MediaStatus.TEMP) return toResponse(mediaFile);
        if (mediaFile.getStatus() != MediaStatus.PENDING) {
            throw new AppException(FileErrorCode.FILE_ALREADY_COMPLETED);
        }

        if (avatar && (mediaFile.getExpiresAt() == null || !mediaFile.getExpiresAt().isAfter(LocalDateTime.now()))) {
            rejectUpload(mediaFile);
            throw new AppException(FileErrorCode.FILE_UPLOAD_INVALID);
        }
        // Nguon su that la storage, khong phai loi khai cua client o buoc presign.
        StoredObject stored = storageService.stat(mediaFile.getStorageKey());
        if (stored == null) {
            throw new AppException(FileErrorCode.FILE_UPLOAD_NOT_COMPLETED);
        }
        if (stored.sizeBytes() <= 0 || stored.sizeBytes() > (avatar ? MAX_AVATAR_BYTES : minioProperties.maxUploadBytes())) {
            rejectUpload(mediaFile);
            throw new AppException(FileErrorCode.FILE_TOO_LARGE);
        }

        String contentType = resolveContentType(stored.contentType());
        if (!(avatar ? AVATAR_CONTENT_TYPES : ALLOWED_CONTENT_TYPES).contains(contentType)) {
            rejectUpload(mediaFile);
            throw new AppException(FileErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        if (avatar && (!contentType.equals(mediaFile.getContentType())
                || !matchesSignature(contentType, storageService.readPrefix(mediaFile.getStorageKey(), 32)))) {
            rejectUpload(mediaFile);
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
        mediaFile.setStatus(avatar ? MediaStatus.TEMP : MediaStatus.ACTIVE);
        if (avatar) mediaFile.setVisibility(MediaVisibility.PRIVATE);

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
    @Transactional(readOnly = true)
    public MediaFileResponse getById(String id, String ownerId) {
        return toResponse(findOwnedFile(id, ownerId));
    }

    @Override
    @Transactional
    public MediaFileResponse update(String id, MediaFileUpdateRequest request) {
        MediaFile mediaFile = findOwnedFile(id);
        if (mediaFile.getPurpose() == MediaPurpose.AVATAR) {
            throw new AppException(FileErrorCode.FILE_ACCESS_DENIED);
        }
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
        if (mediaFile.getPurpose() == MediaPurpose.AVATAR) {
            throw new AppException(FileErrorCode.FILE_ACCESS_DENIED);
        }
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
        if (mediaFile.getPurpose() == MediaPurpose.AVATAR) throw new AppException(FileErrorCode.FILE_ACCESS_DENIED);
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
        if (mediaFile.getPurpose() == MediaPurpose.AVATAR) {
            throw new AppException(FileErrorCode.FILE_ACCESS_DENIED);
        }
        storageService.delete(mediaFile.getStorageKey());
        mediaFileRepository.delete(mediaFile);
    }

    @Override
    @Transactional(readOnly = true)
    public String resolveContentUrl(String id) {
        MediaFile mediaFile = mediaFileRepository.findById(id)
                .orElseThrow(() -> new AppException(FileErrorCode.FILE_NOT_FOUND));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication == null ? null : authentication.getName();
        boolean owner = userId != null && userId.equals(mediaFile.getOwnerId());
        if (mediaFile.getStatus() != MediaStatus.ACTIVE) {
            if (mediaFile.getStatus() != MediaStatus.TEMP || !owner) {
                throw new AppException(FileErrorCode.FILE_NOT_FOUND);
            }
        } else if (mediaFile.getVisibility() != MediaVisibility.PUBLIC) {
            boolean returnsReviewer = "returns".equals(mediaFile.getFolder())
                    && authentication != null
                    && authentication.getAuthorities().stream()
                            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
            if (!owner && !returnsReviewer) {
                throw new AppException(FileErrorCode.FILE_ACCESS_DENIED);
            }
        }
        return storageService.presignDownload(mediaFile.getStorageKey());
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 0 * * * ?")
    public void purgeStalePendingUploads() {
        // Het han presign la het duong upload, nen row PENDING cu hon the khong bao gio
        // complete duoc nua. Nhan doi thoi han de mot lenh complete ve muon khong bi cat.
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(2L * minioProperties.presignExpirySeconds());
        List<String> ids = mediaFileRepository.findPendingIdsBefore(cutoff, PageRequest.of(0, 50));
        for (String id : ids) {
            MediaFile media = mediaFileRepository.findByIdForUpdate(id).orElse(null);
            if (media != null && media.getStatus() == MediaStatus.PENDING && media.getCreatedAt().isBefore(cutoff)) discard(media);
        }
    }

    @Override
    @Transactional(timeout = 30)
    public void activateIfCurrent(ProfileAvatarChangedEvent event) {
        List<MediaFile> ownerMedia = mediaFileRepository.findByOwnerIdAndPurposeOrderByIdAsc(event.userId(), MediaPurpose.AVATAR);
        var reference = currentReference(event.userId());
        if (!event.newMediaId().equals(reference.avatarMediaId()) || !java.util.Objects.equals(event.avatarRevision(), reference.avatarRevision())) return;
        MediaFile media = ownerMedia.stream().filter(m -> m.getId().equals(event.newMediaId())).findFirst()
                .orElseThrow(() -> new AppException(FileErrorCode.FILE_NOT_FOUND));
        activate(media, ownerMedia);
    }

    @Override
    @Transactional(timeout = 30)
    public void reconcileMedia(String id) {
        String ownerId = mediaFileRepository.findOwnerIdById(id).orElse(null);
        if (ownerId == null) return;
        List<MediaFile> ownerMedia = mediaFileRepository.findByOwnerIdAndPurposeOrderByIdAsc(ownerId, MediaPurpose.AVATAR);
        MediaFile media = ownerMedia.stream().filter(m -> m.getId().equals(id)).findFirst().orElse(null);
        if (media == null) return;
        LocalDateTime now = LocalDateTime.now();
        if (media.getStatus() == MediaStatus.TRASHED) {
            if (media.getCreatedAt().isBefore(now.minusSeconds(2L * minioProperties.presignExpirySeconds()))) discard(media);
            return;
        }
        var reference = currentReference(ownerId);
        if (id.equals(reference.avatarMediaId())) {
            activate(media, ownerMedia);
            return;
        }
        if (media.getStatus() == MediaStatus.TEMP && media.getExpiresAt() != null && media.getExpiresAt().isBefore(now.minusMinutes(5))) {
            discard(media);
        } else if (media.getStatus() == MediaStatus.ACTIVE && media.getRetiredAt() != null && media.getRetiredAt().isBefore(now.minusHours(1))
                && ownerMedia.stream().anyMatch(m -> m.getId().equals(reference.avatarMediaId()) && m.getStatus() == MediaStatus.ACTIVE)) {
            discard(media);
        }
    }

    /** Object co the da nam tren storage: xoa ca hai phia de khong de lai rac. */
    private void discard(MediaFile mediaFile) {
        storageService.delete(mediaFile.getStorageKey());
        mediaFileRepository.delete(mediaFile);
    }

    private void rejectUpload(MediaFile media) {
        if (media.getPurpose() == MediaPurpose.AVATAR) {
            // Keep the row until the PUT URL expires, so a late PUT cannot create an orphan.
            media.setStatus(MediaStatus.TRASHED);
            media.setTrashedAt(LocalDateTime.now());
            mediaFileRepository.save(media);
        } else discard(media);
    }

    private void ensureOwner(MediaFile media, String ownerId) {
        if (ownerId == null || !ownerId.equals(media.getOwnerId())) throw new AppException(ErrorCode.UNAUTHORIZED);
    }

    private MediaFile findOwnedFile(String id) {
        return findOwnedFile(id, currentUserProvider.getCurrentUserId());
    }

    private MediaFile findOwnedFile(String id, String ownerId) {
        MediaFile mediaFile = mediaFileRepository.findById(id)
                .orElseThrow(() -> new AppException(FileErrorCode.FILE_NOT_FOUND));
        ensureOwner(mediaFile, ownerId);
        return mediaFile;
    }

    private boolean matchesSignature(String contentType, byte[] prefix) {
        if (prefix == null || prefix.length < 3) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> prefix.length >= 3
                    && (prefix[0] & 0xFF) == 0xFF
                    && (prefix[1] & 0xFF) == 0xD8
                    && (prefix[2] & 0xFF) == 0xFF;
            case "image/png" -> prefix.length >= 8
                    && (prefix[0] & 0xFF) == 0x89
                    && prefix[1] == 0x50
                    && prefix[2] == 0x4E
                    && prefix[3] == 0x47
                    && prefix[4] == 0x0D
                    && prefix[5] == 0x0A
                    && prefix[6] == 0x1A
                    && prefix[7] == 0x0A;
            case "image/webp" -> prefix.length >= 12
                    && prefix[0] == 'R' && prefix[1] == 'I' && prefix[2] == 'F' && prefix[3] == 'F'
                    && prefix[8] == 'W' && prefix[9] == 'E' && prefix[10] == 'B' && prefix[11] == 'P';
            default -> false;
        };
    }

    private IdentityAvatarClient.AvatarReferenceResponse currentReference(String ownerId) {
        var response = identityAvatarClient.getAvatarReference(ownerId);
        if (response == null || response.getData() == null) throw new IllegalStateException("Identity avatar reference unavailable");
        return response.getData();
    }

    private void activate(MediaFile media, List<MediaFile> ownerMedia) {
        if (media.getStatus() != MediaStatus.TEMP && media.getStatus() != MediaStatus.ACTIVE)
            throw new AppException(FileErrorCode.FILE_UPLOAD_INVALID);
        media.setStatus(MediaStatus.ACTIVE);
        media.setVisibility(MediaVisibility.PUBLIC);
        media.setExpiresAt(null);
        media.setRetiredAt(null);
        mediaFileRepository.save(media);
        for (MediaFile previous : ownerMedia) {
            if (!previous.getId().equals(media.getId()) && previous.getStatus() == MediaStatus.ACTIVE && previous.getRetiredAt() == null) {
                previous.setRetiredAt(LocalDateTime.now());
                mediaFileRepository.save(previous);
            }
        }
    }

    private MediaFileResponse toResponse(MediaFile mediaFile) {
        return mediaFileMapper.toResponse(mediaFile, storageProperties.publicBaseUrl());
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
