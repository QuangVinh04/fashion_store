package com.fashionstore.catalog.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.catalog.config.FileStorageProperties;
import com.fashionstore.catalog.dto.MediaFileContent;
import com.fashionstore.catalog.dto.MediaFileResponse;
import com.fashionstore.catalog.dto.MediaFileUpdateRequest;
import com.fashionstore.catalog.dto.StoredFile;
import com.fashionstore.catalog.exception.FileErrorCode;
import com.fashionstore.catalog.mapper.MediaFileMapper;
import com.fashionstore.catalog.model.MediaFile;
import com.fashionstore.catalog.model.enumeration.MediaStatus;
import com.fashionstore.catalog.model.enumeration.MediaType;
import com.fashionstore.catalog.model.enumeration.MediaVisibility;
import com.fashionstore.catalog.repository.MediaFileRepository;
import com.fashionstore.catalog.repository.MediaFileSpecifications;
import com.fashionstore.catalog.service.MediaFileService;
import com.fashionstore.catalog.service.StorageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MediaFileServiceImpl implements MediaFileService {

    MediaFileRepository mediaFileRepository;
    StorageService storageService;
    CurrentUserProvider currentUserProvider;
    MediaFileMapper mediaFileMapper;
    FileStorageProperties storageProperties;

    @Override
    @Transactional
    public MediaFileResponse upload(MultipartFile file,
                                    String displayName,
                                    String altText,
                                    String folder,
                                    List<String> tags,
                                    MediaVisibility visibility) {
        if (file == null || file.isEmpty()) {
            throw new AppException(FileErrorCode.FILE_UPLOAD_INVALID);
        }

        byte[] content = readBytes(file);
        String ownerId = currentUserProvider.getCurrentUserId();
        String originalFilename = cleanFilename(file.getOriginalFilename());
        String contentType = resolveContentType(file.getContentType());
        MediaType mediaType = resolveMediaType(contentType);
        StoredFile storedFile = storageService.store(content, originalFilename);
        ImageDimensions dimensions = readImageDimensions(content, mediaType);

        MediaFile mediaFile = MediaFile.builder()
                .ownerId(ownerId)
                .originalFilename(originalFilename)
                .displayName(resolveDisplayName(displayName, originalFilename))
                .storedFilename(storedFile.storedFilename())
                .storageKey(storedFile.storageKey())
                .contentType(contentType)
                .extension(storedFile.extension())
                .sizeBytes((long) content.length)
                .checksumSha256(sha256(content))
                .mediaType(mediaType)
                .visibility(visibility == null ? MediaVisibility.PUBLIC : visibility)
                .altText(cleanNullable(altText))
                .folder(cleanFolder(folder))
                .width(dimensions.width())
                .height(dimensions.height())
                .tags(normalizeTags(tags))
                .build();

        try {
            return toResponse(mediaFileRepository.save(mediaFile));
        } catch (RuntimeException exception) {
            storageService.delete(storedFile.storageKey());
            throw exception;
        }
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
        MediaFile mediaFile = findOwnedFile(id);
        return toResponse(mediaFile);
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
    public MediaFileContent loadContent(String id) {
        MediaFile mediaFile = mediaFileRepository.findById(id)
                .orElseThrow(() -> new AppException(FileErrorCode.FILE_NOT_FOUND));
        ensureContentAccess(mediaFile);
        Resource resource = storageService.load(mediaFile.getStorageKey());
        return new MediaFileContent(resource, mediaFile.getContentType(), mediaFile.getOriginalFilename());
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

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new AppException(FileErrorCode.FILE_UPLOAD_INVALID, exception);
        }
    }

    private String cleanFilename(String filename) {
        String cleaned = cleanNullable(filename);
        if (cleaned == null) {
            return "untitled";
        }
        String normalized = cleaned.replace("\\", "/");
        return normalized.substring(normalized.lastIndexOf('/') + 1);
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

    private ImageDimensions readImageDimensions(byte[] content, MediaType mediaType) {
        if (mediaType != MediaType.IMAGE) {
            return new ImageDimensions(null, null);
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                return new ImageDimensions(null, null);
            }
            return new ImageDimensions(image.getWidth(), image.getHeight());
        } catch (IOException exception) {
            log.debug("Could not read image dimensions", exception);
            return new ImageDimensions(null, null);
        }
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new AppException(FileErrorCode.FILE_STORAGE_FAILED, exception);
        }
    }

    private record ImageDimensions(Integer width, Integer height) {
    }
}
