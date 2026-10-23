package com.fashionstore.catalog.media.service;

import com.fashionstore.catalog.media.dto.CompleteUploadRequest;
import com.fashionstore.catalog.media.dto.MediaFileResponse;
import com.fashionstore.catalog.media.dto.MediaFileUpdateRequest;
import com.fashionstore.catalog.media.dto.PresignUploadRequest;
import com.fashionstore.catalog.media.dto.PresignUploadResponse;
import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.catalog.media.entity.MediaStatus;
import com.fashionstore.catalog.media.entity.MediaType;
import com.fashionstore.catalog.media.entity.MediaPurpose;
import com.fashionstore.contracts.identity.event.ProfileAvatarChangedEvent;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MediaFileService {

    PresignUploadResponse presignUpload(PresignUploadRequest request);

    PresignUploadResponse presignUpload(PresignUploadRequest request, MediaPurpose purpose);

    MediaFileResponse completeUpload(String id, CompleteUploadRequest request);

    PageResponse<List<MediaFileResponse>> search(Pageable pageable,
                                                 String keyword,
                                                 MediaType mediaType,
                                                 String folder,
                                                 MediaStatus status);

    MediaFileResponse getById(String id);

    MediaFileResponse getById(String id, String ownerId);

    void activateIfCurrent(ProfileAvatarChangedEvent event);

    void reconcileMedia(String id);

    MediaFileResponse update(String id, MediaFileUpdateRequest request);

    void moveToTrash(String id);

    MediaFileResponse restore(String id);

    void deletePermanently(String id);

    /** URL da ky de doc noi dung, dung cho redirect tu {@code /api/v1/files/{id}/content}. */
    String resolveContentUrl(String id);

    String resolveEvidenceUrl(String id, String ownerId);

    /** Don row {@code PENDING} qua han presign â€” presign roi bo do se de lai rac vinh vien. */
    void purgeStalePendingUploads();
}
