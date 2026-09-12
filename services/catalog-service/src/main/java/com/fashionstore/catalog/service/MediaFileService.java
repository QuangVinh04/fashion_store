package com.fashionstore.catalog.service;

import com.fashionstore.catalog.dto.CompleteUploadRequest;
import com.fashionstore.catalog.dto.MediaFileResponse;
import com.fashionstore.catalog.dto.MediaFileUpdateRequest;
import com.fashionstore.catalog.dto.PresignUploadRequest;
import com.fashionstore.catalog.dto.PresignUploadResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.catalog.entity.enumeration.MediaStatus;
import com.fashionstore.catalog.entity.enumeration.MediaType;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MediaFileService {

    PresignUploadResponse presignUpload(PresignUploadRequest request);

    MediaFileResponse completeUpload(String id, CompleteUploadRequest request);

    PageResponse<List<MediaFileResponse>> search(Pageable pageable,
                                                 String keyword,
                                                 MediaType mediaType,
                                                 String folder,
                                                 MediaStatus status);

    MediaFileResponse getById(String id);

    MediaFileResponse update(String id, MediaFileUpdateRequest request);

    void moveToTrash(String id);

    MediaFileResponse restore(String id);

    void deletePermanently(String id);

    /** URL da ky de doc noi dung, dung cho redirect tu {@code /api/v1/files/{id}/content}. */
    String resolveContentUrl(String id);

    /** Don row {@code PENDING} qua han presign — presign roi bo do se de lai rac vinh vien. */
    void purgeStalePendingUploads();
}
