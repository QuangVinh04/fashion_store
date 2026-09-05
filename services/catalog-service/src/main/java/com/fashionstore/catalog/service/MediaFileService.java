package com.fashionstore.catalog.service;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.catalog.dto.MediaFileContent;
import com.fashionstore.catalog.dto.MediaFileResponse;
import com.fashionstore.catalog.dto.MediaFileUpdateRequest;
import com.fashionstore.catalog.model.enumeration.MediaStatus;
import com.fashionstore.catalog.model.enumeration.MediaType;
import com.fashionstore.catalog.model.enumeration.MediaVisibility;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MediaFileService {

    MediaFileResponse upload(MultipartFile file,
                             String displayName,
                             String altText,
                             String folder,
                             List<String> tags,
                             MediaVisibility visibility);

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

    MediaFileContent loadContent(String id);
}
