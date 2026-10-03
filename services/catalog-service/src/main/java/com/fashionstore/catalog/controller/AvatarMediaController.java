package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.PresignUploadRequest;
import com.fashionstore.catalog.dto.MediaFileResponse;
import com.fashionstore.catalog.dto.PresignUploadResponse;
import com.fashionstore.catalog.service.MediaFileService;
import com.fashionstore.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/files/avatars")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AvatarMediaController {

    MediaFileService mediaFileService;

    @PostMapping("/presign")
    public ApiResponse<PresignUploadResponse> presign(@Valid @RequestBody PresignUploadRequest request) {
        return ApiResponse.<PresignUploadResponse>builder()
                .message("Create avatar upload url successfully")
                .data(mediaFileService.presignUpload(request, com.fashionstore.catalog.entity.enumeration.MediaPurpose.AVATAR))
                .build();
    }

    @PostMapping("/{mediaId}/complete")
    public ApiResponse<MediaFileResponse> complete(@PathVariable String mediaId) {
        return ApiResponse.<MediaFileResponse>builder()
                .message("Complete avatar upload successfully")
                .data(mediaFileService.completeUpload(mediaId, null))
                .build();
    }
}
