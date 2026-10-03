package com.fashionstore.catalog.service.impl;

import com.fashionstore.catalog.config.FileStorageProperties;
import com.fashionstore.catalog.dto.PresignUploadRequest;
import com.fashionstore.catalog.dto.MediaFileResponse;
import com.fashionstore.catalog.dto.PresignUploadResponse;
import com.fashionstore.catalog.dto.PresignedUpload;
import com.fashionstore.catalog.dto.StoredObject;
import com.fashionstore.catalog.entity.MediaFile;
import com.fashionstore.catalog.entity.enumeration.MediaPurpose;
import com.fashionstore.catalog.entity.enumeration.MediaStatus;
import com.fashionstore.catalog.entity.enumeration.MediaType;
import com.fashionstore.catalog.entity.enumeration.MediaVisibility;
import com.fashionstore.catalog.exception.FileErrorCode;
import com.fashionstore.catalog.mapper.MediaFileMapper;
import com.fashionstore.catalog.repository.MediaFileRepository;
import com.fashionstore.catalog.service.StorageService;
import com.fashionstore.catalog.client.IdentityAvatarClient;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.contracts.identity.event.ProfileAvatarChangedEvent;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaFileAvatarLifecycleTest {

    private static final byte[] JPEG_PREFIX = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
    private static final byte[] PNG_PREFIX = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] WEBP_PREFIX = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0, 0};

    @Mock
    MediaFileRepository mediaFileRepository;
    @Mock
    StorageService storageService;
    @Mock
    CurrentUserProvider currentUserProvider;
    @Mock
    MediaFileMapper mediaFileMapper;
    @Mock
    com.fashionstore.catalog.client.IdentityAvatarClient identityAvatarClient;

    MediaFileServiceImpl avatarMediaService;

    @BeforeEach
    void setUp() {
        avatarMediaService = new MediaFileServiceImpl(identityAvatarClient, mediaFileRepository, storageService,
                currentUserProvider, mediaFileMapper, new FileStorageProperties("http://localhost:8087"),
                new com.fashionstore.catalog.config.MinioProperties("http://minio:9000", "http://localhost:9000", "minioadmin", "minioadmin", "fashion-media", 900, 20L * 1024 * 1024));

        lenient().when(mediaFileMapper.toResponse(any(MediaFile.class), anyString()))
                .thenAnswer(inv -> {
                    MediaFile file = inv.getArgument(0);
                    return MediaFileResponse.builder()
                            .id(file.getId())
                            .status(file.getStatus())
                            .visibility(file.getVisibility())
                            .sizeBytes(file.getSizeBytes())
                            .url("http://localhost:8087/api/v1/files/" + file.getId() + "/content")
                            .build();
                });
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private PresignUploadRequest validPresignRequest(String contentType, long size) {
        return PresignUploadRequest.builder()
                .filename("avatar." + (contentType.contains("jpeg") ? "jpg" : contentType.contains("png") ? "png" : "webp"))
                .contentType(contentType)
                .sizeBytes(size)
                .build();
    }

    private MediaFile pendingAvatarFile(String contentType) {
        MediaFile mediaFile = MediaFile.builder()
                .ownerId("user-1")
                .originalFilename("avatar.png")
                .displayName("avatar.png")
                .storedFilename("obj-avatar.png")
                .storageKey("avatars/2026/10/obj-avatar.png")
                .contentType(contentType)
                .extension("png")
                .sizeBytes(2048L)
                .mediaType(MediaType.IMAGE)
                .status(MediaStatus.PENDING)
                .purpose(MediaPurpose.AVATAR)
                .visibility(MediaVisibility.PRIVATE)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        mediaFile.setId("avatar-media-1");
        return mediaFile;
    }

    @Test
    void presignCreatesPendingAvatarWithPrivateVisibilityAnd24hExpiry() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(storageService.presignUpload(anyString(), eq("image/jpeg"), any()))
                .thenReturn(new PresignedUpload("http://localhost:9000/upload", 900));
        when(mediaFileRepository.save(any(MediaFile.class))).thenAnswer(inv -> {
            MediaFile file = inv.getArgument(0);
            file.setId("avatar-1");
            return file;
        });

        PresignUploadResponse response = avatarMediaService.presignUpload(validPresignRequest("image/jpeg", 1024L), MediaPurpose.AVATAR);

        ArgumentCaptor<MediaFile> captor = ArgumentCaptor.forClass(MediaFile.class);
        verify(mediaFileRepository).save(captor.capture());
        MediaFile saved = captor.getValue();

        assertThat(saved.getOwnerId()).isEqualTo("user-1");
        assertThat(saved.getStatus()).isEqualTo(MediaStatus.PENDING);
        assertThat(saved.getPurpose()).isEqualTo(MediaPurpose.AVATAR);
        assertThat(saved.getVisibility()).isEqualTo(MediaVisibility.PRIVATE);
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusHours(23));
        assertThat(response.getMediaId()).isEqualTo("avatar-1");
    }

    @Test
    void presignRejectsUnsupportedContentType() {
        PresignUploadRequest request = PresignUploadRequest.builder()
                .filename("doc.pdf")
                .contentType("application/pdf")
                .sizeBytes(1024L)
                .build();

        assertThatThrownBy(() -> avatarMediaService.presignUpload(request, MediaPurpose.AVATAR))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void presignRejectsOversizedAvatarAbove5MiB() {
        PresignUploadRequest request = validPresignRequest("image/png", 5L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> avatarMediaService.presignUpload(request, MediaPurpose.AVATAR))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void completeTransitionsPendingToTempReadyForValidJpeg() {
        MediaFile file = pendingAvatarFile("image/jpeg");
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(mediaFileRepository.findByIdForUpdate("avatar-media-1")).thenReturn(Optional.of(file));
        when(storageService.stat(file.getStorageKey())).thenReturn(new StoredObject(5000L, "image/jpeg", "etag-1"));
        when(storageService.readPrefix(eq(file.getStorageKey()), anyInt())).thenReturn(JPEG_PREFIX);
        when(mediaFileRepository.save(file)).thenReturn(file);

        MediaFileResponse response = avatarMediaService.completeUpload("avatar-media-1", null);

        assertThat(file.getStatus()).isEqualTo(MediaStatus.TEMP);
        assertThat(file.getVisibility()).isEqualTo(MediaVisibility.PRIVATE);
        assertThat(response.getStatus()).isEqualTo(MediaStatus.TEMP);
    }

    @Test
    void completeTransitionsPendingToTempReadyForValidPng() {
        MediaFile file = pendingAvatarFile("image/png");
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(mediaFileRepository.findByIdForUpdate("avatar-media-1")).thenReturn(Optional.of(file));
        when(storageService.stat(file.getStorageKey())).thenReturn(new StoredObject(5000L, "image/png", "etag-2"));
        when(storageService.readPrefix(eq(file.getStorageKey()), anyInt())).thenReturn(PNG_PREFIX);
        when(mediaFileRepository.save(file)).thenReturn(file);

        MediaFileResponse response = avatarMediaService.completeUpload("avatar-media-1", null);

        assertThat(file.getStatus()).isEqualTo(MediaStatus.TEMP);
    }

    @Test
    void completeTransitionsPendingToTempReadyForValidWebp() {
        MediaFile file = pendingAvatarFile("image/webp");
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(mediaFileRepository.findByIdForUpdate("avatar-media-1")).thenReturn(Optional.of(file));
        when(storageService.stat(file.getStorageKey())).thenReturn(new StoredObject(5000L, "image/webp", "etag-3"));
        when(storageService.readPrefix(eq(file.getStorageKey()), anyInt())).thenReturn(WEBP_PREFIX);
        when(mediaFileRepository.save(file)).thenReturn(file);

        MediaFileResponse response = avatarMediaService.completeUpload("avatar-media-1", null);

        assertThat(file.getStatus()).isEqualTo(MediaStatus.TEMP);
    }

    @Test
    void completeRetriedOnTempReadyReturnsSameRowWithoutActivating() {
        MediaFile file = pendingAvatarFile("image/png");
        file.setStatus(MediaStatus.TEMP);
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(mediaFileRepository.findByIdForUpdate("avatar-media-1")).thenReturn(Optional.of(file));

        MediaFileResponse response = avatarMediaService.completeUpload("avatar-media-1", null);

        assertThat(response.getStatus()).isEqualTo(MediaStatus.TEMP);
        verify(storageService, never()).stat(anyString());
        verify(storageService, never()).readPrefix(anyString(), anyInt());
        verify(mediaFileRepository, never()).save(any());
    }

    @Test
    void completeRejectsWrongOwner() {
        MediaFile file = pendingAvatarFile("image/png");
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-other");
        when(mediaFileRepository.findByIdForUpdate("avatar-media-1")).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> avatarMediaService.completeUpload("avatar-media-1", null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void completeFailsWhenObjectMissingOnStorage() {
        MediaFile file = pendingAvatarFile("image/png");
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(mediaFileRepository.findByIdForUpdate("avatar-media-1")).thenReturn(Optional.of(file));
        when(storageService.stat(file.getStorageKey())).thenReturn(null);

        assertThatThrownBy(() -> avatarMediaService.completeUpload("avatar-media-1", null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_UPLOAD_NOT_COMPLETED);
    }

    @Test
    void completeFailsAndDiscardsWhenRealSizeExceeds5MiB() {
        MediaFile file = pendingAvatarFile("image/png");
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(mediaFileRepository.findByIdForUpdate("avatar-media-1")).thenReturn(Optional.of(file));
        when(storageService.stat(file.getStorageKey()))
                .thenReturn(new StoredObject(5L * 1024 * 1024 + 1, "image/png", "etag-x"));

        assertThatThrownBy(() -> avatarMediaService.completeUpload("avatar-media-1", null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_TOO_LARGE);

        assertThat(file.getStatus()).isEqualTo(MediaStatus.TRASHED);
        verify(storageService, never()).delete(anyString());
        verify(mediaFileRepository).save(file);
    }

    @Test
    void completeFailsAndDiscardsWhenFileSignatureMismatch() {
        MediaFile file = pendingAvatarFile("image/png");
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(mediaFileRepository.findByIdForUpdate("avatar-media-1")).thenReturn(Optional.of(file));
        when(storageService.stat(file.getStorageKey()))
                .thenReturn(new StoredObject(2048L, "image/png", "etag-x"));
        // Trả về prefix của JPEG thay vì PNG
        when(storageService.readPrefix(eq(file.getStorageKey()), anyInt())).thenReturn(JPEG_PREFIX);

        assertThatThrownBy(() -> avatarMediaService.completeUpload("avatar-media-1", null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_TYPE_NOT_ALLOWED);

        assertThat(file.getStatus()).isEqualTo(MediaStatus.TRASHED);
        verify(storageService, never()).delete(anyString());
        verify(mediaFileRepository).save(file);
    }

    @Test
    void completeFailsAndDiscardsWhenExpired() {
        MediaFile file = pendingAvatarFile("image/png");
        file.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(mediaFileRepository.findByIdForUpdate("avatar-media-1")).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> avatarMediaService.completeUpload("avatar-media-1", null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_UPLOAD_INVALID);

        assertThat(file.getStatus()).isEqualTo(MediaStatus.TRASHED);
        verify(storageService, never()).delete(anyString());
        verify(mediaFileRepository).save(file);
    }
    @Test
    void activateIfCurrentMatchesIdentityReferencePromotesClaimedToActiveAndRetiresPreviousActive() {
        ProfileAvatarChangedEvent event = new ProfileAvatarChangedEvent("user-1", "avatar-old", "avatar-new", 2L);
        when(identityAvatarClient.getAvatarReference("user-1")).thenReturn(
                ApiResponse.<IdentityAvatarClient.AvatarReferenceResponse>builder()
                        .data(new IdentityAvatarClient.AvatarReferenceResponse("avatar-new", 2L))
                        .build()
        );

        MediaFile newMedia = pendingAvatarFile("image/png");
        newMedia.setId("avatar-new");
        newMedia.setStatus(MediaStatus.TEMP);

        MediaFile oldMedia = pendingAvatarFile("image/png");
        oldMedia.setId("avatar-old");
        oldMedia.setStatus(MediaStatus.ACTIVE);
        oldMedia.setRetiredAt(null);

        when(mediaFileRepository.findByOwnerIdAndPurposeOrderByIdAsc("user-1", MediaPurpose.AVATAR))
                .thenReturn(List.of(oldMedia, newMedia));
        when(mediaFileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        avatarMediaService.activateIfCurrent(event);

        assertThat(newMedia.getStatus()).isEqualTo(MediaStatus.ACTIVE);
        assertThat(newMedia.getVisibility()).isEqualTo(MediaVisibility.PUBLIC);
        assertThat(newMedia.getRetiredAt()).isNull();
        assertThat(oldMedia.getRetiredAt()).isNotNull();

        verify(mediaFileRepository).save(newMedia);
        verify(mediaFileRepository).save(oldMedia);
    }

    @Test
    void activateIfCurrentDuplicateWhenAlreadyActiveIsIdempotent() {
        ProfileAvatarChangedEvent event = new ProfileAvatarChangedEvent("user-1", "avatar-old", "avatar-new", 2L);
        when(identityAvatarClient.getAvatarReference("user-1")).thenReturn(
                ApiResponse.<IdentityAvatarClient.AvatarReferenceResponse>builder()
                        .data(new IdentityAvatarClient.AvatarReferenceResponse("avatar-new", 2L))
                        .build()
        );

        MediaFile newMedia = pendingAvatarFile("image/png");
        newMedia.setId("avatar-new");
        newMedia.setStatus(MediaStatus.ACTIVE);
        newMedia.setVisibility(MediaVisibility.PUBLIC);

        when(mediaFileRepository.findByOwnerIdAndPurposeOrderByIdAsc("user-1", MediaPurpose.AVATAR))
                .thenReturn(List.of(newMedia));

        avatarMediaService.activateIfCurrent(event);

        assertThat(newMedia.getStatus()).isEqualTo(MediaStatus.ACTIVE);
        assertThat(newMedia.getRetiredAt()).isNull();
    }

    @Test
    void activateIfCurrentStaleEventLeavesMediaStagedAndDoesNotRetireActiveImage() {
        ProfileAvatarChangedEvent event = new ProfileAvatarChangedEvent("user-1", "avatar-old", "avatar-new", 2L);
        when(identityAvatarClient.getAvatarReference("user-1")).thenReturn(
                ApiResponse.<IdentityAvatarClient.AvatarReferenceResponse>builder()
                        .data(new IdentityAvatarClient.AvatarReferenceResponse("avatar-newer", 3L))
                        .build()
        );

        avatarMediaService.activateIfCurrent(event);

        verify(mediaFileRepository, never()).findByIdForUpdate(any());
        verify(mediaFileRepository, never()).save(any());
    }

    @Test
    void activateIfCurrentThrowsWhenIdentityFails() {
        ProfileAvatarChangedEvent event = new ProfileAvatarChangedEvent("user-1", "avatar-old", "avatar-new", 2L);
        when(identityAvatarClient.getAvatarReference("user-1")).thenThrow(new RuntimeException("Identity timeout"));

        assertThatThrownBy(() -> avatarMediaService.activateIfCurrent(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Identity timeout");

        verify(mediaFileRepository, never()).findByIdForUpdate(any());
        verify(mediaFileRepository, never()).save(any());
    }
}
