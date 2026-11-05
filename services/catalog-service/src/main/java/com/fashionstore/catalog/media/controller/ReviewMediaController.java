package com.fashionstore.catalog.media.controller;

import com.fashionstore.catalog.media.dto.PresignUploadRequest;
import com.fashionstore.catalog.media.dto.PresignUploadResponse;
import com.fashionstore.catalog.media.entity.MediaPurpose;
import com.fashionstore.catalog.media.service.MediaFileService;
import com.fashionstore.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/files/reviews")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReviewMediaController {

    MediaFileService mediaFileService;

    @Operation(summary = "Cấp URL upload ảnh đánh giá",
            description = "Ảnh JPEG/PNG/WebP ≤ 5MB. PUT file lên `uploadUrl`, gọi `POST /api/v1/files/{id}/complete`, "
                    + "rồi gửi `mediaId` trong `mediaIds` của đánh giá.")
    @PostMapping("/presign")
    public ApiResponse<PresignUploadResponse> presign(@Valid @RequestBody PresignUploadRequest request) {
        return ApiResponse.<PresignUploadResponse>builder()
                .message("Create review image upload url successfully")
                .data(mediaFileService.presignUpload(request, MediaPurpose.REVIEW_IMAGE))
                .build();
    }
}
