package com.fashionstore.catalog.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.catalog.dto.CompleteUploadRequest;
import com.fashionstore.catalog.dto.MediaFileResponse;
import com.fashionstore.catalog.dto.MediaFileUpdateRequest;
import com.fashionstore.catalog.dto.PresignUploadRequest;
import com.fashionstore.catalog.dto.PresignUploadResponse;
import com.fashionstore.catalog.model.enumeration.MediaStatus;
import com.fashionstore.catalog.model.enumeration.MediaType;
import com.fashionstore.catalog.model.enumeration.MediaVisibility;
import com.fashionstore.catalog.service.MediaFileService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MediaFileController {

    MediaFileService mediaFileService;

    /** Buoc 1: cap URL da ky, browser se PUT thang file len storage. */
    @PostMapping("/presign")
    public ApiResponse<PresignUploadResponse> presignUpload(@Valid @RequestBody PresignUploadRequest request) {
        return ApiResponse.<PresignUploadResponse>builder()
                .message("Create upload url successfully")
                .data(mediaFileService.presignUpload(request))
                .build();
    }

    /** Buoc 3: xac nhan object da nam tren storage roi moi kich hoat ban ghi. */
    @PostMapping("/{id}/complete")
    public ApiResponse<MediaFileResponse> completeUpload(@PathVariable String id,
                                                         @Valid @RequestBody(required = false)
                                                         CompleteUploadRequest request) {
        return ApiResponse.<MediaFileResponse>builder()
                .message("Complete upload successfully")
                .data(mediaFileService.completeUpload(id, request))
                .build();
    }

    @GetMapping
    public ApiResponse<PageResponse<List<MediaFileResponse>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) MediaType mediaType,
            @RequestParam(required = false) String folder,
            @RequestParam(required = false, defaultValue = "ACTIVE") MediaStatus status,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.<PageResponse<List<MediaFileResponse>>>builder()
                .message("Get files successfully")
                .data(mediaFileService.search(pageable, keyword, mediaType, folder, status))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<MediaFileResponse> getById(@PathVariable String id) {
        return ApiResponse.<MediaFileResponse>builder()
                .message("Get file successfully")
                .data(mediaFileService.getById(id))
                .build();
    }

    /** Giu nguyen duong dan cu, nhung tra 302 sang presigned GET thay vi stream qua service. */
    @GetMapping("/{id}/content")
    public ResponseEntity<Void> getContent(@PathVariable String id) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(mediaFileService.resolveContentUrl(id)))
                .build();
    }

    @PatchMapping("/{id}")
    public ApiResponse<MediaFileResponse> update(@PathVariable String id,
                                                 @Valid @RequestBody MediaFileUpdateRequest request) {
        return ApiResponse.<MediaFileResponse>builder()
                .message("Update file successfully")
                .data(mediaFileService.update(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> moveToTrash(@PathVariable String id) {
        mediaFileService.moveToTrash(id);
        return ApiResponse.<Void>builder()
                .message("Move file to trash successfully")
                .build();
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<MediaFileResponse> restore(@PathVariable String id) {
        return ApiResponse.<MediaFileResponse>builder()
                .message("Restore file successfully")
                .data(mediaFileService.restore(id))
                .build();
    }

    @DeleteMapping("/{id}/permanent")
    public ApiResponse<Void> deletePermanently(@PathVariable String id) {
        mediaFileService.deletePermanently(id);
        return ApiResponse.<Void>builder()
                .message("Delete file permanently successfully")
                .build();
    }
}
