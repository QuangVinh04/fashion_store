package com.fashionstore.catalog.media.controller;

import com.fashionstore.catalog.media.dto.PresignUploadRequest;
import com.fashionstore.catalog.media.dto.PresignUploadResponse;
import com.fashionstore.catalog.media.entity.MediaPurpose;
import com.fashionstore.catalog.media.service.MediaFileService;
import com.fashionstore.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/files/evidence")
@RequiredArgsConstructor
public class EvidenceUploadController {
    private final MediaFileService media;

    @PostMapping("/returns/presign")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PresignUploadResponse> returns(@Valid @RequestBody PresignUploadRequest request) {
        return ApiResponse.<PresignUploadResponse>builder().data(media.presignUpload(request, MediaPurpose.RETURN_EVIDENCE)).build();
    }

    @PostMapping("/payments/presign")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PresignUploadResponse> payments(@Valid @RequestBody PresignUploadRequest request) {
        return ApiResponse.<PresignUploadResponse>builder().data(media.presignUpload(request, MediaPurpose.PAYMENT_EVIDENCE)).build();
    }
}
