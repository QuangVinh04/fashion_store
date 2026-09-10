package com.fashionstore.catalog.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.catalog.config.FileStorageProperties;
import com.fashionstore.catalog.dto.MediaFileResponse;
import com.fashionstore.catalog.dto.StoredFile;
import com.fashionstore.catalog.exception.FileErrorCode;
import com.fashionstore.catalog.mapper.MediaFileMapperImpl;
import com.fashionstore.catalog.model.MediaFile;
import com.fashionstore.catalog.model.enumeration.MediaStatus;
import com.fashionstore.catalog.model.enumeration.MediaType;
import com.fashionstore.catalog.model.enumeration.MediaVisibility;
import com.fashionstore.catalog.repository.MediaFileRepository;
import com.fashionstore.catalog.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
                new FileStorageProperties("/tmp/file-service-test", "http://cdn.local")
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadStoresFileAndReturnsMediaMetadata() {
        byte[] imageBytes = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hero.png",
                "image/png",
                imageBytes
        );

        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(storageService.store(imageBytes, "hero.png"))
                .thenReturn(new StoredFile("2026/07/file.png", "file.png", "png"));
        when(mediaFileRepository.save(any(MediaFile.class))).thenAnswer(invocation -> {
            MediaFile mediaFile = invocation.getArgument(0);
            mediaFile.setId("file-1");
            return mediaFile;
        });

        MediaFileResponse response = mediaFileService.upload(
                file,
                "Hero banner",
                "Home page hero",
                "/campaigns/summer/",
                java.util.List.of("Homepage", "Summer", "homepage"),
                MediaVisibility.PUBLIC
        );

        assertThat(response.getId()).isEqualTo("file-1");
        assertThat(response.getDisplayName()).isEqualTo("Hero banner");
        assertThat(response.getMediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(response.getFolder()).isEqualTo("campaigns/summer");
        assertThat(response.getTags()).containsExactly("homepage", "summer");
        assertThat(response.getUrl()).isEqualTo("http://cdn.local/api/v1/files/file-1/content");
        assertThat(response.getChecksumSha256()).hasSize(64);
    }

    @Test
    void loadContentRejectsPrivateFileWithoutAuthentication() {
        MediaFile mediaFile = MediaFile.builder()
                .ownerId("user-1")
                .originalFilename("private.pdf")
                .storageKey("2026/07/private.pdf")
                .contentType("application/pdf")
                .status(MediaStatus.ACTIVE)
                .visibility(MediaVisibility.PRIVATE)
                .build();
        mediaFile.setId("file-1");

        when(mediaFileRepository.findById("file-1")).thenReturn(Optional.of(mediaFile));

        assertThatThrownBy(() -> mediaFileService.loadContent("file-1"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_ACCESS_DENIED);

        verify(storageService, never()).load(any());
    }

    @Test
    void restoreMovesTrashedFileBackToActive() {
        MediaFile mediaFile = MediaFile.builder()
                .ownerId("user-1")
                .status(MediaStatus.TRASHED)
                .visibility(MediaVisibility.PUBLIC)
                .originalFilename("lookbook.jpg")
                .displayName("lookbook.jpg")
                .storedFilename("lookbook.jpg")
                .storageKey("2026/07/lookbook.jpg")
                .contentType("image/jpeg")
                .extension("jpg")
                .sizeBytes(128L)
                .checksumSha256("0".repeat(64))
                .mediaType(MediaType.IMAGE)
                .trashedAt(LocalDateTime.now())
                .build();
        mediaFile.setId("file-1");

        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(mediaFileRepository.findById("file-1")).thenReturn(Optional.of(mediaFile));
        when(mediaFileRepository.save(mediaFile)).thenReturn(mediaFile);

        MediaFileResponse response = mediaFileService.restore("file-1");

        assertThat(response.getStatus()).isEqualTo(MediaStatus.ACTIVE);
        assertThat(response.getTrashedAt()).isNull();
    }
}
