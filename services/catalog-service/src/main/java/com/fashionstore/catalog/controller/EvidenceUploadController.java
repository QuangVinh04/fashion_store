package com.fashionstore.catalog.controller;

import com.fashionstore.catalog.dto.*;
import com.fashionstore.catalog.entity.enumeration.MediaPurpose;
import com.fashionstore.catalog.service.MediaFileService;
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
