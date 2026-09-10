package com.fashionstore.catalog.service.impl;

import com.fashionstore.catalog.config.FileStorageProperties;
import com.fashionstore.catalog.config.MinioProperties;
import com.fashionstore.catalog.dto.CompleteUploadRequest;
import com.fashionstore.catalog.dto.MediaFileResponse;
import com.fashionstore.catalog.dto.PresignUploadRequest;
import com.fashionstore.catalog.dto.PresignUploadResponse;
import com.fashionstore.catalog.dto.PresignedUpload;
import com.fashionstore.catalog.dto.StoredObject;
import com.fashionstore.catalog.exception.FileErrorCode;
import com.fashionstore.catalog.mapper.MediaFileMapperImpl;
import com.fashionstore.catalog.model.MediaFile;
import com.fashionstore.catalog.model.enumeration.MediaStatus;
import com.fashionstore.catalog.model.enumeration.MediaType;
import com.fashionstore.catalog.model.enumeration.MediaVisibility;
import com.fashionstore.catalog.repository.MediaFileRepository;
import com.fashionstore.catalog.service.StorageService;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaFileServiceImplTest {

    @Mock
    MediaFileRepository mediaFileRepository;
    @Mock
    StorageService storageService;
    @Mock
    CurrentUserProvider currentUserProvider;

    MediaFileServiceImpl mediaFileService;

    @BeforeEach
    void setUp() {
        mediaFileService = new MediaFileServiceImpl(
                mediaFileRepository,
                storageService,
                currentUserProvider,
                new MediaFileMapperImpl(),
                new FileStorageProperties("http://localhost:8087"),
                new MinioProperties(
                        "http://minio:9000",
                        "http://localhost:9000",
                        "minioadmin",
                        "minioadmin",
                        "fashion-media",
                        900,
                        20L * 1024 * 1024)
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private PresignUploadRequest presignRequest() {
        PresignUploadRequest request = new PresignUploadRequest();
        request.setFilename("../hero shot.PNG");
        request.setContentType("image/png");
        request.setSizeBytes(2048L);
        request.setDisplayName("Hero banner");
        request.setFolder("/campaigns/summer/");
        request.setTags(List.of("Homepage", "Summer", "homepage"));
        return request;
    }

    @Test
    void presignUploadCreatesPendingRowAndReturnsUploadUrl() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(storageService.presignUpload(anyString(), eq("image/png")))
                .thenReturn(new PresignedUpload("http://localhost:9000/fashion-media/key?sig=x", 900));
        when(mediaFileRepository.save(any(MediaFile.class))).thenAnswer(invocation -> {
            MediaFile saved = invocation.getArgument(0);
            saved.setId("file-1");
            return saved;
        });

        PresignUploadResponse response = mediaFileService.presignUpload(presignRequest());

        ArgumentCaptor<MediaFile> captor = ArgumentCaptor.forClass(MediaFile.class);
        verify(mediaFileRepository).save(captor.capture());
        MediaFile saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(MediaStatus.PENDING);
        assertThat(saved.getOwnerId()).isEqualTo("user-1");
        // ten goc bi lam sach, khong con phan duong dan
        assertThat(saved.getOriginalFilename()).isEqualTo("hero shot.PNG");
        assertThat(saved.getStorageKey()).matches("\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png");
        assertThat(saved.getMediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(saved.getFolder()).isEqualTo("campaigns/summer");
        assertThat(saved.getTags()).containsExactly("homepage", "summer");
        assertThat(saved.getChecksumSha256()).isNull();

        assertThat(response.getMediaId()).isEqualTo("file-1");
        assertThat(response.getUploadUrl()).isEqualTo("http://localhost:9000/fashion-media/key?sig=x");
        assertThat(response.getExpiresInSeconds()).isEqualTo(900);
        assertThat(response.getStorageKey()).isEqualTo(saved.getStorageKey());
    }

    @Test
    void presignUploadRejectsContentTypeOutsideAllowList() {
        PresignUploadRequest request = presignRequest();
        request.setContentType("text/html");

        assertThatThrownBy(() -> mediaFileService.presignUpload(request))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_TYPE_NOT_ALLOWED);

        verify(mediaFileRepository, never()).save(any());
    }

    @Test
    void presignUploadRejectsDeclaredSizeAboveLimit() {
        PresignUploadRequest request = presignRequest();
        request.setSizeBytes(21L * 1024 * 1024);

        assertThatThrownBy(() -> mediaFileService.presignUpload(request))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void completeUploadVerifiesObjectThenActivatesRow() {
        MediaFile pending = pendingFile();
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(mediaFileRepository.findById("file-1")).thenReturn(Optional.of(pending));
        when(storageService.stat("2026/09/object.png"))
                .thenReturn(new StoredObject(4096L, "image/png", "etag-1"));
        when(mediaFileRepository.save(pending)).thenReturn(pending);

        CompleteUploadRequest request = new CompleteUploadRequest();
        request.setWidth(800);
        request.setHeight(600);

        MediaFileResponse response = mediaFileService.completeUpload("file-1", request);

        assertThat(response.getStatus()).isEqualTo(MediaStatus.ACTIVE);
        // kich thuoc that tren storage thay cho so client khai luc presign
        assertThat(response.getSizeBytes()).isEqualTo(4096L);
        assertThat(response.getWidth()).isEqualTo(800);
        assertThat(response.getHeight()).isEqualTo(600);
        assertThat(pending.getEtag()).isEqualTo("etag-1");
        assertThat(response.getUrl()).isEqualTo("http://localhost:8087/api/v1/files/file-1/content");
    }

    @Test
    void completeUploadFailsWhenObjectMissingOnStorage() {
        MediaFile pending = pendingFile();
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(mediaFileRepository.findById("file-1")).thenReturn(Optional.of(pending));
        when(storageService.stat("2026/09/object.png")).thenReturn(null);

        assertThatThrownBy(() -> mediaFileService.completeUpload("file-1", null))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_UPLOAD_NOT_COMPLETED);

        assertThat(pending.getStatus()).isEqualTo(MediaStatus.PENDING);
        verify(mediaFileRepository, never()).save(any());
    }

    @Test
    void completeUploadRejectsAlreadyActiveFile() {
        MediaFile pending = pendingFile();
        pending.setStatus(MediaStatus.ACTIVE);
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(mediaFileRepository.findById("file-1")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> mediaFileService.completeUpload("file-1", null))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_ALREADY_COMPLETED);

        verify(storageService, never()).stat(anyString());
    }

    @Test
    void completeUploadDropsObjectWhenRealSizeExceedsLimit() {
        MediaFile pending = pendingFile();
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(mediaFileRepository.findById("file-1")).thenReturn(Optional.of(pending));
        when(storageService.stat("2026/09/object.png"))
                .thenReturn(new StoredObject(21L * 1024 * 1024, "image/png", "etag-1"));

        assertThatThrownBy(() -> mediaFileService.completeUpload("file-1", null))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_TOO_LARGE);

        // object da nam tren storage nen phai don, khong de rac
        verify(storageService).delete("2026/09/object.png");
        verify(mediaFileRepository).delete(pending);
    }

    @Test
    void resolveContentUrlReturnsPresignedGetUrl() {
        MediaFile mediaFile = pendingFile();
        mediaFile.setStatus(MediaStatus.ACTIVE);
        when(mediaFileRepository.findById("file-1")).thenReturn(Optional.of(mediaFile));
        when(storageService.presignDownload("2026/09/object.png"))
                .thenReturn("http://localhost:9000/fashion-media/2026/09/object.png?sig=y");

        assertThat(mediaFileService.resolveContentUrl("file-1"))
                .isEqualTo("http://localhost:9000/fashion-media/2026/09/object.png?sig=y");
    }

    @Test
    void resolveContentUrlRejectsPrivateFileWithoutAuthentication() {
        MediaFile mediaFile = pendingFile();
        mediaFile.setStatus(MediaStatus.ACTIVE);
        mediaFile.setVisibility(MediaVisibility.PRIVATE);
        when(mediaFileRepository.findById("file-1")).thenReturn(Optional.of(mediaFile));

        assertThatThrownBy(() -> mediaFileService.resolveContentUrl("file-1"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_ACCESS_DENIED);

        verify(storageService, never()).presignDownload(anyString());
    }

    @Test
    void resolveContentUrlRejectsPendingFile() {
        when(mediaFileRepository.findById("file-1")).thenReturn(Optional.of(pendingFile()));

        assertThatThrownBy(() -> mediaFileService.resolveContentUrl("file-1"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_NOT_FOUND);
    }

    @Test
    void restoreMovesTrashedFileBackToActive() {
        MediaFile mediaFile = pendingFile();
        mediaFile.setStatus(MediaStatus.TRASHED);
        mediaFile.setTrashedAt(LocalDateTime.now());

        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(mediaFileRepository.findById("file-1")).thenReturn(Optional.of(mediaFile));
        when(mediaFileRepository.save(mediaFile)).thenReturn(mediaFile);

        MediaFileResponse response = mediaFileService.restore("file-1");

        assertThat(response.getStatus()).isEqualTo(MediaStatus.ACTIVE);
        assertThat(response.getTrashedAt()).isNull();
    }

    @Test
    void completeUploadDropsObjectWhenStorageContentTypeNotAllowed() {
        MediaFile pending = pendingFile();
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(mediaFileRepository.findById("file-1")).thenReturn(Optional.of(pending));
        // Content-Type khong nam trong chu ky truoc day nen client co the PUT bat ky thu gi
        when(storageService.stat("2026/09/object.png"))
                .thenReturn(new StoredObject(2048L, "text/html", "etag-1"));

        assertThatThrownBy(() -> mediaFileService.completeUpload("file-1", null))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_TYPE_NOT_ALLOWED);

        // object da nam tren storage nen phai don, khong de rac nhu nhanh FILE_TOO_LARGE
        verify(storageService).delete("2026/09/object.png");
        verify(mediaFileRepository).delete(pending);
    }

    @Test
    void purgeStalePendingUploadsRemovesObjectAndRow() {
        MediaFile stale = pendingFile();
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        when(mediaFileRepository.findByStatusAndCreatedAtBefore(eq(MediaStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(stale));

        mediaFileService.purgeStalePendingUploads();

        verify(mediaFileRepository).findByStatusAndCreatedAtBefore(eq(MediaStatus.PENDING), cutoff.capture());
        // moc cat = 2 lan thoi han presign, du cho mot lenh complete ve muon
        assertThat(ChronoUnit.SECONDS.between(cutoff.getValue(), LocalDateTime.now()))
                .isBetween(1795L, 1805L);
        verify(storageService).delete("2026/09/object.png");
        verify(mediaFileRepository).delete(stale);
    }

    @Test
    void purgeStalePendingUploadsDoesNothingWhenNoStaleRow() {
        when(mediaFileRepository.findByStatusAndCreatedAtBefore(eq(MediaStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of());

        mediaFileService.purgeStalePendingUploads();

        verify(storageService, never()).delete(anyString());
        verify(mediaFileRepository, never()).delete(any(MediaFile.class));
    }

    private MediaFile pendingFile() {
        MediaFile mediaFile = MediaFile.builder()
                .ownerId("user-1")
                .originalFilename("hero.png")
                .displayName("Hero banner")
                .storedFilename("object.png")
                .storageKey("2026/09/object.png")
                .contentType("image/png")
                .extension("png")
                .sizeBytes(2048L)
                .mediaType(MediaType.IMAGE)
                .status(MediaStatus.PENDING)
                .visibility(MediaVisibility.PUBLIC)
                .build();
        mediaFile.setId("file-1");
        return mediaFile;
    }
}
