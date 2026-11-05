package com.fashionstore.catalog.media.service.impl;

import com.fashionstore.catalog.media.api.ReviewImage;
import com.fashionstore.catalog.media.config.FileStorageProperties;
import com.fashionstore.catalog.media.entity.MediaFile;
import com.fashionstore.catalog.media.entity.MediaPurpose;
import com.fashionstore.catalog.media.entity.MediaStatus;
import com.fashionstore.catalog.media.exception.FileErrorCode;
import com.fashionstore.catalog.media.mapper.MediaFileMapper;
import com.fashionstore.catalog.media.repository.MediaFileRepository;
import com.fashionstore.common.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewImagesImplTest {

    @Mock
    MediaFileRepository mediaFileRepository;

    ReviewImagesImpl reviewImages;

    @BeforeEach
    void setUp() {
        reviewImages = new ReviewImagesImpl(
                mediaFileRepository, Mappers.getMapper(MediaFileMapper.class), new FileStorageProperties("http://localhost:8087"));
    }

    @Test
    void returnsImagesInRequestedOrderWithPublicContentUrl() {
        when(mediaFileRepository.findAllById(List.of("m-2", "m-1")))
                .thenReturn(List.of(image("m-1", "user-1", MediaPurpose.REVIEW_IMAGE, MediaStatus.ACTIVE),
                        image("m-2", "user-1", MediaPurpose.REVIEW_IMAGE, MediaStatus.ACTIVE)));

        List<ReviewImage> images = reviewImages.requireOwned("user-1", List.of("m-2", "m-1"));

        assertThat(images).extracting(ReviewImage::mediaId).containsExactly("m-2", "m-1");
        assertThat(images.get(0).url()).isEqualTo("http://localhost:8087/api/v1/files/m-2/content");
    }

    @Test
    void rejectsImageOfAnotherUser() {
        when(mediaFileRepository.findAllById(List.of("m-1")))
                .thenReturn(List.of(image("m-1", "user-2", MediaPurpose.REVIEW_IMAGE, MediaStatus.ACTIVE)));

        assertThatThrownBy(() -> reviewImages.requireOwned("user-1", List.of("m-1")))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_NOT_FOUND);
    }

    @Test
    void rejectsAvatarOrUnfinishedUpload() {
        when(mediaFileRepository.findAllById(List.of("avatar", "pending")))
                .thenReturn(List.of(image("avatar", "user-1", MediaPurpose.AVATAR, MediaStatus.ACTIVE),
                        image("pending", "user-1", MediaPurpose.REVIEW_IMAGE, MediaStatus.PENDING)));

        assertThatThrownBy(() -> reviewImages.requireOwned("user-1", List.of("avatar", "pending")))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_UPLOAD_INVALID);
    }

    @Test
    void rejectsMissingImage() {
        when(mediaFileRepository.findAllById(List.of("missing"))).thenReturn(List.of());

        assertThatThrownBy(() -> reviewImages.requireOwned("user-1", List.of("missing")))
                .isInstanceOf(AppException.class);
    }

    private static MediaFile image(String id, String ownerId, MediaPurpose purpose, MediaStatus status) {
        MediaFile file = MediaFile.builder().ownerId(ownerId).purpose(purpose).status(status).build();
        file.setId(id);
        return file;
    }
}
