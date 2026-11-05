package com.fashionstore.catalog.media.service.impl;

import com.fashionstore.catalog.media.api.ReviewImage;
import com.fashionstore.catalog.media.api.ReviewImages;
import com.fashionstore.catalog.media.config.FileStorageProperties;
import com.fashionstore.catalog.media.entity.MediaFile;
import com.fashionstore.catalog.media.entity.MediaPurpose;
import com.fashionstore.catalog.media.entity.MediaStatus;
import com.fashionstore.catalog.media.exception.FileErrorCode;
import com.fashionstore.catalog.media.mapper.MediaFileMapper;
import com.fashionstore.catalog.media.repository.MediaFileRepository;
import com.fashionstore.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewImagesImpl implements ReviewImages {

    private final MediaFileRepository mediaFileRepository;
    private final MediaFileMapper mediaFileMapper;
    private final FileStorageProperties storageProperties;

    @Override
    @Transactional(readOnly = true)
    public List<ReviewImage> requireOwned(String ownerId, List<String> mediaIds) {
        if (mediaIds.isEmpty()) {
            return List.of();
        }
        Map<String, MediaFile> files = mediaFileRepository.findAllById(mediaIds).stream()
                .collect(Collectors.toMap(MediaFile::getId, Function.identity()));
        return mediaIds.stream()
                .map(id -> toReviewImage(files.get(id), ownerId))
                .toList();
    }

    private ReviewImage toReviewImage(MediaFile file, String ownerId) {
        if (file == null || !ownerId.equals(file.getOwnerId())) {
            throw new AppException(FileErrorCode.FILE_NOT_FOUND);
        }
        if (file.getPurpose() != MediaPurpose.REVIEW_IMAGE || file.getStatus() != MediaStatus.ACTIVE) {
            throw new AppException(FileErrorCode.FILE_UPLOAD_INVALID);
        }
        return new ReviewImage(file.getId(), mediaFileMapper.resolveUrl(file, storageProperties.publicBaseUrl()));
    }
}
